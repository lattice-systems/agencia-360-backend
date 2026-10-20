package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ERP-1.0 de punta a punta: Facturación elige la unidad, el vendedor finaliza
 * el prospecto en el CRM y, con el evento ProspectoFinalizado, inventario
 * registra la venta y la salida de la unidad. El evento se atiende después del
 * commit y en otro hilo, así que la prueba confirma sus datos, espera el
 * resultado y borra todo al terminar.
 */
@AutoConfigureMockMvc
class VentaAlFinalizarIntegrationTest extends AbstractIntegrationTest {

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

	private Usuario almacen;
	private Usuario facturacion;
	private Usuario vendedor;
	private final List<Long> prospectos = new ArrayList<>();
	private final List<Long> vehiculos = new ArrayList<>();

	@BeforeEach
	void setUp() {
		almacen = registrar("almacen.finalizar", "INVENTARIO");
		facturacion = registrar("facturacion.finalizar", "FACTURACION");
		vendedor = registrar("vendedor.finalizar", "VENTAS");
	}

	@AfterEach
	void limpiar() {
		for (Long id : prospectos) {
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
	void alFinalizarSeRegistraLaVentaYLaUnidadSaleDelInventario() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY188888");
		long prospecto = prospectoEn("Luis Herrera", "4775550188", "FACTURACION");
		mockMvc.perform(conToken(put("/api/v1/ventas/{id}/unidad", prospecto), token(facturacion))
				.contentType(MediaType.APPLICATION_JSON).content("{\"vehiculoId\":%d}".formatted(sentra)))
				.andExpect(status().isOk());
		jdbcTemplate.update("update prospecto set etapa = 'ENTREGA' where id = ?", prospecto);

		finalizar(prospecto);

		Map<String, Object> venta = esperar(() -> {
			List<Map<String, Object>> filas = jdbcTemplate.queryForList(
					"select estatus, registrada_en from venta where prospecto_id = ? and estatus = 'REGISTRADA'",
					prospecto);
			return filas.isEmpty() ? null : filas.get(0);
		});
		assertThat(venta.get("registrada_en")).isNotNull();
		Map<String, Object> salida = jdbcTemplate.queryForMap(
				"select responsable_id, cantidad from movimiento_inventario where vehiculo_id = ? and tipo = 'SALIDA'",
				sentra);
		assertThat(((Number) salida.get("responsable_id")).longValue()).isEqualTo(vendedor.getId());
		assertThat(((Number) salida.get("cantidad")).intValue()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, sentra))
				.isEqualTo("VENDIDO");
	}

	@Test
	void sinUnidadElegidaLaVentaSeRegistraSinVehiculo() throws Exception {
		long prospecto = prospectoEn("Carlos Núñez", "4775550189", "ENTREGA");

		finalizar(prospecto);

		Map<String, Object> venta = esperar(() -> {
			List<Map<String, Object>> filas = jdbcTemplate
					.queryForList("select estatus, vehiculo_id from venta where prospecto_id = ?", prospecto);
			return filas.isEmpty() ? null : filas.get(0);
		});
		assertThat(venta.get("estatus")).isEqualTo("REGISTRADA");
		assertThat(venta.get("vehiculo_id")).isNull();
	}

	private void finalizar(long prospecto) throws Exception {
		mockMvc.perform(conToken(patch("/api/v1/prospectos/{id}/etapa", prospecto), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"etapa":"FINALIZADO","clienteRechazaApp":true}
						""")).andExpect(status().isOk());
	}

	private long prospectoEn(String nombre, String telefono, String etapa) throws Exception {
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/prospectos"), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","telefono":"%s","correo":null}
								""".formatted(nombre, telefono)))
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

	/** Espera a que el listener del evento termine; falla si no pasa a tiempo. */
	private static <T> T esperar(Supplier<T> consulta) throws InterruptedException {
		Instant limite = Instant.now().plus(ESPERA);
		while (Instant.now().isBefore(limite)) {
			T resultado = consulta.get();
			if (resultado != null) {
				return resultado;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("El evento no se atendió en " + ESPERA.toSeconds() + " s");
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
