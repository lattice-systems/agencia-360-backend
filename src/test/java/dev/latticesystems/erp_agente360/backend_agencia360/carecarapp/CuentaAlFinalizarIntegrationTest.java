package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * CRM-1.3 de punta a punta: Facturación elige la unidad, el vendedor finaliza
 * el prospecto y, con los eventos, se registra al cliente con su vehículo y se
 * abre su cuenta de la app. Los eventos se atienden después del commit y en
 * otro hilo, así que la prueba confirma sus datos, espera el resultado y borra
 * todo al terminar.
 */
@AutoConfigureMockMvc
class CuentaAlFinalizarIntegrationTest extends AbstractIntegrationTest {

	private static final Duration ESPERA = Duration.ofSeconds(10);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Usuario almacen;
	private Usuario facturacion;
	private Usuario vendedor;
	private final List<Long> prospectos = new ArrayList<>();
	private final List<Long> vehiculos = new ArrayList<>();

	@BeforeEach
	void setUp() {
		almacen = registrar("almacen.cuenta", "INVENTARIO");
		facturacion = registrar("facturacion.cuenta", "FACTURACION");
		vendedor = registrar("vendedor.cuenta", "VENTAS");
	}

	@AfterEach
	void limpiar() throws InterruptedException {
		for (Long id : prospectos) {
			esperar(() -> jdbcTemplate.queryForList("select id from cliente where prospecto_id = ?", id).stream()
					.findFirst().orElse(null));
			List<Long> clientes = jdbcTemplate.queryForList("select id from cliente where prospecto_id = ?", Long.class,
					id);
			for (Long cliente : clientes) {
				jdbcTemplate.update("delete from sesion_app where cuenta_id in (select id from cuenta_app where "
						+ "cliente_id = ?)", cliente);
				jdbcTemplate.update("delete from cuenta_app where cliente_id = ?", cliente);
				jdbcTemplate.update("delete from vehiculo_cliente where cliente_id = ?", cliente);
				jdbcTemplate.update("delete from cliente where id = ?", cliente);
			}
			jdbcTemplate.update("delete from venta where prospecto_id = ?", id);
		}
		for (Long id : vehiculos) {
			jdbcTemplate.update("delete from movimiento_inventario where vehiculo_id = ?", id);
			jdbcTemplate.update("delete from vehiculo where id = ?", id);
		}
		for (Long id : prospectos) {
			jdbcTemplate.update("delete from historial_etapa where prospecto_id = ?", id);
			jdbcTemplate.update("delete from prospecto where id = ?", id);
		}
		jdbcTemplate.update("delete from usuario where id in (?, ?, ?)", almacen.getId(), facturacion.getId(),
				vendedor.getId());
	}

	@Test
	void alFinalizarSeRegistraElClienteConSuVehiculoYSeAbreSuCuenta() throws Exception {
		long unidad = altaDeVehiculo("3N1AB8CV5PY177777");
		long prospecto = prospectoEn("Marta Gil", "marta.gil@correo.com", "FACTURACION");
		mockMvc.perform(conToken(put("/api/v1/ventas/{id}/unidad", prospecto), token(facturacion))
				.contentType(MediaType.APPLICATION_JSON).content("{\"vehiculoId\":%d}".formatted(unidad)))
				.andExpect(status().isOk());
		jdbcTemplate.update("update prospecto set etapa = 'ENTREGA' where id = ?", prospecto);

		finalizar(prospecto, false);

		Map<String, Object> cuenta = esperar(() -> jdbcTemplate.queryForList(
				"select c.id, c.debe_cambiar_contrasena from cuenta_app c join cliente cl on cl.id = c.cliente_id "
						+ "where cl.prospecto_id = ?",
				prospecto).stream().findFirst().orElse(null));
		assertThat(cuenta.get("debe_cambiar_contrasena")).isEqualTo(true);
		Map<String, Object> vehiculo = jdbcTemplate.queryForMap(
				"select v.vehiculo_id, v.vin, v.modelo, v.anio from "
						+ "vehiculo_cliente v join cliente c on c.id = v.cliente_id where c.prospecto_id = ?",
				prospecto);
		assertThat(((Number) vehiculo.get("vehiculo_id")).longValue()).isEqualTo(unidad);
		assertThat(vehiculo.get("vin")).isEqualTo("3N1AB8CV5PY177777");
		assertThat(vehiculo.get("modelo")).isEqualTo("Sentra");

		ArgumentCaptor<String> temporal = ArgumentCaptor.forClass(String.class);
		verify(envioCorreos, timeout(ESPERA.toMillis())).enviarCuentaNueva(eq("marta.gil@correo.com"), eq("Marta Gil"),
				temporal.capture());
		mockMvc.perform(post("/api/v1/app/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"marta.gil@correo.com\",\"contrasena\":\"%s\"}".formatted(temporal.getValue())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarContrasena").value(true));
	}

	@Test
	void siElClienteNoQuiereLaAppSeRegistraSinCuenta() throws Exception {
		long prospecto = prospectoEn("Raúl Vega", "raul.vega@correo.com", "ENTREGA");

		finalizar(prospecto, true);

		Map<String, Object> cliente = esperar(
				() -> jdbcTemplate.queryForList("select id, correo from cliente where prospecto_id = ?", prospecto)
						.stream().findFirst().orElse(null));
		assertThat(cliente.get("correo")).isNull();
		Thread.sleep(500);
		assertThat(jdbcTemplate.queryForObject("select count(*) from cuenta_app where cliente_id = ?", Integer.class,
				cliente.get("id"))).isZero();
		verify(envioCorreos, never()).enviarCuentaNueva(anyString(), anyString(), anyString());
	}

	private void finalizar(long prospecto, boolean rechazaApp) throws Exception {
		mockMvc.perform(conToken(patch("/api/v1/prospectos/{id}/etapa", prospecto), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"FINALIZADO\",\"clienteRechazaApp\":%b}".formatted(rechazaApp)))
				.andExpect(status().isOk());
	}

	private long prospectoEn(String nombre, String correo, String etapa) throws Exception {
		String cuerpo = "{\"nombre\":\"%s\",\"telefono\":null,\"correo\":\"%s\"}".formatted(nombre, correo);
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/prospectos"), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		prospectos.add(id);
		jdbcTemplate.update("update prospecto set etapa = ? where id = ?", etapa, id);
		return id;
	}

	private long altaDeVehiculo(String vin) throws Exception {
		String cuerpo = """
				{"vin":"%s","modelo":"Sentra","version":"Advance","anio":2026,"color":"Blanco","precio":489900.00}
				""".formatted(vin);
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/vehiculos"), token(almacen)).contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		vehiculos.add(id);
		return id;
	}

	/** Espera a que los listeners terminen; falla si no pasa a tiempo. */
	private static <T> T esperar(Supplier<T> consulta) throws InterruptedException {
		Instant limite = Instant.now().plus(ESPERA);
		while (Instant.now().isBefore(limite)) {
			T resultado = consulta.get();
			if (resultado != null) {
				return resultado;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Los eventos no se atendieron en " + ESPERA.toSeconds() + " s");
	}

	private Usuario registrar(String usuario, String rol) {
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario,
				rolRepository.findByNombre(rol).orElseThrow()));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
