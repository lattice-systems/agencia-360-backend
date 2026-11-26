package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ParametroTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ParametroTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Valuación formal de punta a punta (V2-M26.01 a V2-M26.04) contra PostgreSQL
 * real. No revierte cada prueba porque el prospecto se crea después del commit:
 * cada una siembra sus clientes y su marca, y al terminar borra sus citas para
 * no gastar el cupo de otras pruebas.
 */
@AutoConfigureMockMvc
class ValuacionIntegrationTest extends AbstractIntegrationTest {

	private static final String RUTA = "/api/v1/app/valuaciones";

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
	private ParametroTomaCuentaRepository parametros;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private record Cliente(long id, String correo, long vehiculo, String token) {
	}

	private final List<Long> sembrados = new ArrayList<>();
	private String unico;
	private String marca;
	private Cliente ana;
	private Cliente beto;

	@BeforeEach
	void sembrar() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		marca = "M" + unico;
		parametros.save(ParametroTomaCuenta.nuevo(marca, "March", 2020, new BigDecimal("200000"),
				new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.10")));
		usuario("vendedor-" + unico, "VENTAS", "Vera Vendedora");
		ana = cliente("ana", "A");
		beto = cliente("beto", "B");
	}

	@AfterEach
	void liberarLaAgenda() {
		for (Long cliente : sembrados) {
			jdbc.update("delete from valuacion where cliente_id = ?", cliente);
			jdbc.update("delete from cita where cliente_id = ?", cliente);
		}
	}

	@Test
	void pedirLaValuacionLaCreaSolicitadaYRepetirDevuelveLaMisma() throws Exception {
		long estimacion = estimar(ana);

		String creada = pedir(ana, estimacion).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("SOLICITADA")).andExpect(jsonPath("$.vehiculoId").value(ana.vehiculo()))
				.andExpect(jsonPath("$.estimacion.id").value(estimacion)).andExpect(jsonPath("$.fotos.length()").value(0))
				.andExpect(jsonPath("$.puedeAgendar").value(true)).andExpect(jsonPath("$.cita").isEmpty())
				.andExpect(jsonPath("$.oferta").isEmpty()).andExpect(jsonPath("$.creadaEn").isString()).andReturn()
				.getResponse().getContentAsString();
		long id = numero(creada, "$.id");

		pedir(ana, estimar(ana)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
		assertThat(jdbc.queryForObject("select count(*) from valuacion where cliente_id = ?", Integer.class, ana.id()))
				.isEqualTo(1);
	}

	@Test
	void elProspectoNaceConOrigenAppYElAsesorApareceEnLaValuacion() throws Exception {
		long id = solicitada(ana);

		long prospecto = esperarProspecto(id);

		assertThat(jdbc.queryForObject("select origen from historial_etapa where prospecto_id = ? order by id limit 1",
				String.class, prospecto)).isEqualTo("APP");
		assertThat(jdbc.queryForObject("select correo from prospecto where id = ?", String.class, prospecto))
				.isEqualTo(ana.correo());
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.asesor").isNotEmpty());
	}

	@Test
	void reutilizaElProspectoAbiertoDeEstreno() throws Exception {
		long modelo = jdbc.queryForObject("""
				insert into modelo_catalogo (marca, modelo, anio, version, precio_desde, activo, created_at, updated_at)
				values ('Nissan', ?, 2026, 'Advance', 300000, true, now(), now()) returning id
				""", Long.class, "Kicks" + unico);
		comoCliente(ana, json(post("/api/v1/app/estreno/solicitudes"), "{\"tipo\":\"COTIZACION\",\"modeloCatalogoId\":"
				+ modelo + ",\"enganche\":60000,\"plazoMeses\":48}")).andExpect(status().isCreated());
		long delEstreno = esperar("select prospecto_id from solicitud_estreno where cliente_id = ?", ana.id());

		long id = solicitada(ana);

		assertThat(jdbc.queryForObject("select prospecto_id from valuacion where id = ?", Long.class, id))
				.isEqualTo(delEstreno);
	}

	@Test
	void unaEstimacionSinVehiculoDa422YUnaAjena404() throws Exception {
		String aMano = comoCliente(ana, json(post("/api/v1/app/toma-a-cuenta/estimaciones"),
				"{\"marca\":\"" + marca + "\",\"modelo\":\"March\",\"anio\":2020,\"km\":1000,\"estado\":\"BUENO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		pedir(ana, numero(aMano, "$.id")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("VALUACION_SIN_VEHICULO"));
		pedir(ana, estimar(beto)).andExpect(status().isNotFound());
		pedir(ana, Long.MAX_VALUE).andExpect(status().isNotFound());
		comoCliente(ana, json(post(RUTA), "{}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void siElVehiculoYaNoEsDelClienteDa404() throws Exception {
		long estimacion = estimar(ana);
		jdbc.update("update vehiculo_cliente set cliente_id = ? where id = ?", beto.id(), ana.vehiculo());

		pedir(ana, estimacion).andExpect(status().isNotFound());
	}

	@Test
	void laListaEsSoloDelClienteYLoAjenoDa404() throws Exception {
		long deAna = solicitada(ana);
		solicitada(beto);

		comoCliente(ana, get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(deAna));
		comoCliente(ana, get(RUTA + "?tamano=51")).andExpect(status().isBadRequest());
		comoCliente(beto, get(RUTA + "/{id}", deAna)).andExpect(status().isNotFound());
		comoCliente(ana, get(RUTA + "/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
	}

	@Test
	void sinTokenDa401YElPersonalOUnaCuentaDesactivada403() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		mockMvc.perform(json(post(RUTA), "{\"estimacionTomaCuentaId\":1}")).andExpect(status().isUnauthorized());
		String gerente = jwtService.generateToken(ana.id(), "gerente", List.of(Role.VENTAS));
		mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + gerente))
				.andExpect(status().isForbidden());

		cuentasAppService.desactivar(ana.id());
		comoCliente(ana, get(RUTA)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
	}

	private Cliente cliente(String nombre, String letra) {
		String correo = nombre + "-" + unico + "@correo.com";
		long id = registroClientesService.registrar(new NuevoCliente(nombre + " " + unico, correo, null, null)).id();
		sembrados.add(id);
		String vin = (letra + "VAL" + unico + "000000000").substring(0, 17).toUpperCase(Locale.ROOT);
		long vehiculo = registroClientesService
				.asignarVehiculo(id, new VehiculoNuevo(null, vin, marca, "March", 2020, null)).vehiculos().getFirst()
				.id();
		cuentasAppService.abrir(id, "Temporal123");
		return new Cliente(id, correo, vehiculo,
				jwtService.generateToken(id, correo, List.of(Role.CLIENTE_CARECAR)));
	}

	private Usuario usuario(String nombreUsuario, String rol, String nombre) {
		return usuarioRepository.save(
				new Usuario(nombreUsuario, "hash-de-prueba", nombre, rolRepository.findByNombre(rol).orElseThrow()));
	}

	private long estimar(Cliente cliente) throws Exception {
		return numero(comoCliente(cliente, json(post("/api/v1/app/toma-a-cuenta/estimaciones"),
				"{\"vehiculoId\":" + cliente.vehiculo() + ",\"km\":60000,\"estado\":\"BUENO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions pedir(Cliente cliente, long estimacion) throws Exception {
		return comoCliente(cliente, json(post(RUTA), "{\"estimacionTomaCuentaId\":" + estimacion + "}"));
	}

	private long solicitada(Cliente cliente) throws Exception {
		return numero(pedir(cliente, estimar(cliente)).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id");
	}

	private long esperarProspecto(long valuacion) throws InterruptedException {
		return esperar("select prospecto_id from valuacion where id = ?", valuacion);
	}

	private long esperar(String consulta, long id) throws InterruptedException {
		for (int intento = 0; intento < 100; intento++) {
			Long valor = jdbc.queryForObject(consulta, Long.class, id);
			if (valor != null) {
				return valor;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("El prospecto no se ligó a tiempo");
	}

	private static long numero(String cuerpo, String ruta) {
		return JsonPath.<Number>read(cuerpo, ruta).longValue();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private ResultActions comoCliente(Cliente cliente, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()));
	}
}
