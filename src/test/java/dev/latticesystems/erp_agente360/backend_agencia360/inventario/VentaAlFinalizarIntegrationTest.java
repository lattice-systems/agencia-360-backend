package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrega de la unidad de punta a punta: la unidad se vendió al emitir la
 * factura (ERP-1.3), el vendedor finaliza el prospecto en el CRM y, con el
 * evento ProspectoFinalizado, inventario registra la salida de la unidad y la
 * venta queda entregada. El evento se atiende después del commit y en otro
 * hilo, así que la prueba confirma sus datos, espera el resultado y borra todo
 * al terminar.
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

	@Autowired
	private VentaDeUnidades ventaDeUnidades;

	@Autowired
	private TransactionTemplate transaccion;

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
	void limpiar() throws InterruptedException {
		// Clientes (CRM-1.3) también atiende el evento y registra al cliente con su
		// vehículo: se espera a que termine para borrar en orden.
		for (Long id : prospectos) {
			esperarCliente(id);
			jdbcTemplate.update("delete from vehiculo_cliente where cliente_id in "
					+ "(select id from cliente where prospecto_id = ?)", id);
			jdbcTemplate.update("delete from cliente where prospecto_id = ?", id);
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
	void alFinalizarLaUnidadSaleDelInventarioYLaVentaQuedaEntregada() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY188888");
		long prospecto = prospectoEn("Luis Herrera", "4775550188", "FACTURACION");
		transaccion.executeWithoutResult(
				estado -> ventaDeUnidades.vender(prospecto, sentra, facturacion.getId(), Instant.now()));
		jdbcTemplate.update("update prospecto set etapa = 'ENTREGA' where id = ?", prospecto);

		finalizar(prospecto);

		Map<String, Object> venta = esperar(() -> {
			List<Map<String, Object>> filas = jdbcTemplate.queryForList(
					"select estatus, entregada_en from venta where prospecto_id = ? and estatus = 'ENTREGADA'",
					prospecto);
			return filas.isEmpty() ? null : filas.get(0);
		});
		assertThat(venta.get("entregada_en")).isNotNull();
		Map<String, Object> salida = esperar(() -> jdbcTemplate
				.queryForList("select responsable_id, cantidad from movimiento_inventario where vehiculo_id = ? "
						+ "and tipo = 'SALIDA'", sentra)
				.stream().findFirst().orElse(null));
		assertThat(((Number) salida.get("responsable_id")).longValue()).isEqualTo(vendedor.getId());
		assertThat(((Number) salida.get("cantidad")).intValue()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, sentra))
				.isEqualTo("VENDIDO");
	}

	@Test
	void sinFacturaNoSeRegistraVentaAlFinalizar() throws Exception {
		long prospecto = prospectoEn("Carlos Núñez", "4775550189", "ENTREGA");

		finalizar(prospecto);

		// Se espera al otro listener del evento (clientes) para saber que ya se
		// atendió.
		esperar(() -> jdbcTemplate.queryForList("select id from cliente where prospecto_id = ?", prospecto).stream()
				.findFirst().orElse(null));
		Thread.sleep(300);
		assertThat(jdbcTemplate.queryForObject("select count(*) from venta where prospecto_id = ?", Integer.class,
				prospecto)).isZero();
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

	/** Solo los prospectos que se finalizaron generan cliente. */
	private void esperarCliente(long prospecto) throws InterruptedException {
		String etapa = jdbcTemplate.queryForObject("select etapa from prospecto where id = ?", String.class, prospecto);
		if ("FINALIZADO".equals(etapa)) {
			esperar(() -> jdbcTemplate.queryForList("select id from cliente where prospecto_id = ?", prospecto).stream()
					.findFirst().orElse(null));
		}
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
