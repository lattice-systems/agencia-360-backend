package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M16 Estreno de punta a punta: cálculo, solicitudes, prospecto en el CRM y
 * estado.
 */
class EstrenoIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String token;
	private long modeloConPrecio;
	private long modeloSinPrecio;

	@BeforeEach
	void clienteYCatalogo() {
		String unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		String correo = "estreno-" + unico + "@correo.com";
		Long cliente = registroClientesService.registrar(new NuevoCliente("Eva Estreno", correo, null, null)).id();
		cuentasAppService.abrir(cliente, "Temporal123");
		token = jwtService.generateToken(cliente, correo, List.of(Role.CLIENTE_CARECAR));
		Rol ventas = rolRepository.findByNombre("VENTAS").orElseThrow();
		usuarioRepository.save(new Usuario("vendedor-" + unico, "hash", "Vera Vendedora", ventas));
		modeloConPrecio = jdbc.queryForObject("""
				insert into modelo_catalogo (marca, modelo, anio, version, precio_desde, activo, created_at, updated_at)
				values ('Nissan', ?, 2026, 'Advance', 300000, true, now(), now()) returning id
				""", Long.class, "Kicks" + unico);
		modeloSinPrecio = jdbc.queryForObject("""
				insert into modelo_catalogo (marca, modelo, anio, activo, created_at, updated_at)
				values ('Nissan', ?, 2026, true, now(), now()) returning id
				""", Long.class, "Sentra" + unico);
	}

	@Test
	void calculaLaMensualidadConLeyendaYValidaEngancheYPlazo() throws Exception {
		calcular(modeloConPrecio, "60000", 48).andExpect(status().isOk())
				.andExpect(jsonPath("$.montoFinanciado").value(240000.0))
				.andExpect(jsonPath("$.mensualidad").value(6678.16))
				.andExpect(jsonPath("$.leyenda").value(org.hamcrest.Matchers.containsString("no vinculante")));
		calcular(modeloConPrecio, "1000", 48).andExpect(status().isBadRequest());
		calcular(modeloConPrecio, "60000", 7).andExpect(status().isBadRequest());
		calcular(modeloSinPrecio, "60000", 48).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTRENO_SIN_PRECIO"));
	}

	@Test
	void pedirPruebaCreaElProspectoConOrigenAppYLuegoEstaEnAtencion() throws Exception {
		String manana = LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(1).toString();
		String cuerpo = pedir("{\"tipo\":\"PRUEBA_MANEJO\",\"modeloCatalogoId\":" + modeloConPrecio
				+ ",\"preferenciaFecha\":\"" + manana + "\",\"preferenciaFranja\":\"MANANA\"}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("RECIBIDA")).andReturn()
				.getResponse().getContentAsString();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();

		Long prospecto = esperarProspecto(id);
		assertThat(prospecto).isNotNull();
		String origen = jdbc.queryForObject(
				"select origen from historial_etapa where prospecto_id = ? order by id limit 1", String.class,
				prospecto);
		assertThat(origen).isEqualTo("APP");

		mockMvc.perform(get("/api/v1/app/estreno/solicitudes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].estado").value("EN_ATENCION"))
				.andExpect(jsonPath("$.contenido[0].vendedor").isNotEmpty())
				.andExpect(jsonPath("$.contenido[0].modelo").value(org.hamcrest.Matchers.containsString("Kicks")));

		pedir("{\"tipo\":\"PRUEBA_MANEJO\",\"modeloCatalogoId\":" + modeloConPrecio + ",\"preferenciaFecha\":\""
				+ manana + "\",\"preferenciaFranja\":\"TARDE\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ESTRENO_DUPLICADA"));
	}

	@Test
	void unaCotizacionGuardaLaMensualidadYRechazaDatosMalos() throws Exception {
		pedir("{\"tipo\":\"COTIZACION\",\"modeloCatalogoId\":" + modeloConPrecio
				+ ",\"enganche\":60000,\"plazoMeses\":48}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.cotizacion.mensualidad").value(6678.16));
		pedir("{\"tipo\":\"PRUEBA_MANEJO\",\"modeloCatalogoId\":" + modeloSinPrecio
				+ ",\"preferenciaFecha\":\"2020-01-01\",\"preferenciaFranja\":\"MANANA\"}")
				.andExpect(status().isBadRequest());
		pedir("{\"tipo\":\"COTIZACION\",\"modeloCatalogoId\":" + modeloConPrecio
				+ ",\"enganche\":60000,\"plazoMeses\":36,\"estimacionTomaCuentaId\":" + Long.MAX_VALUE + "}")
				.andExpect(status().isNotFound());
	}

	private ResultActions calcular(long modelo, String enganche, int plazo) throws Exception {
		return mockMvc.perform(post("/api/v1/app/estreno/cotizaciones/calculo")
				.contentType(MediaType.APPLICATION_JSON).content("{\"modeloCatalogoId\":" + modelo + ",\"enganche\":"
						+ enganche + ",\"plazoMeses\":" + plazo + "}")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions pedir(String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/v1/app/estreno/solicitudes").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private Long esperarProspecto(long solicitudId) throws InterruptedException {
		for (int intento = 0; intento < 50; intento++) {
			Long prospecto = jdbc.queryForObject("select prospecto_id from solicitud_estreno where id = ?", Long.class,
					solicitudId);
			if (prospecto != null) {
				return prospecto;
			}
			Thread.sleep(100);
		}
		return null;
	}
}
