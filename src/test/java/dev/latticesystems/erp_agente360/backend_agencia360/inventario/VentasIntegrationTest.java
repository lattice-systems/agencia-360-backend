package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.VentaDeUnidades.UnidadVendida;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Venta de la unidad (ERP-1.3) contra la base: facturacion la vende al emitir
 * la factura, la unidad pasa a Vendido y la consulta de unidades la regresa a
 * los demás módulos. Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class VentasIntegrationTest extends AbstractIntegrationTest {

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
	private EntityManager entityManager;

	@Autowired
	private ConsultaUnidades consultaUnidades;

	@Autowired
	private VentaDeUnidades ventaDeUnidades;

	private Usuario almacen;
	private Usuario facturacion;
	private Usuario vendedor;

	@BeforeEach
	void setUp() {
		almacen = registrar("almacen.ventas", Role.INVENTARIO);
		facturacion = registrar("facturacion.ventas", Role.FACTURACION);
		vendedor = registrar("vendedor.ventas", Role.VENTAS);
	}

	@Test
	void alVenderLaUnidadPasaAVendidoYSeRegistraLaVenta() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		UnidadVendida vendida = vender(prospecto, sentra);

		assertThat(vendida.vehiculoId()).isEqualTo(sentra);
		assertThat(vendida.ventaId()).isNotNull();
		assertThat(vendida)
				.extracting(UnidadVendida::vin, UnidadVendida::marca, UnidadVendida::modelo, UnidadVendida::version,
						UnidadVendida::anio, UnidadVendida::color)
				.containsExactly("3N1AB8CV5PY123456", "Nissan", "Sentra", "Advance", 2026, "Blanco");
		assertThat(estatusDe(sentra)).isEqualTo("VENDIDO");
		mockMvc.perform(conToken(get("/api/v1/ventas"), token(facturacion))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].prospectoId").value(prospecto))
				.andExpect(jsonPath("$[0].vehiculoId").value(sentra))
				.andExpect(jsonPath("$[0].precio").value(489900.00))
				.andExpect(jsonPath("$[0].estatus").value("REGISTRADA"))
				.andExpect(jsonPath("$[0].registradaEn").isNotEmpty())
				.andExpect(jsonPath("$[0].entregadaEn").isEmpty());
	}

	@Test
	void unaUnidadVendidaNoSeVendeAOtroProspecto() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long luis = prospectoEnFacturacion("Luis Herrera", "4775550104");
		long carlos = prospectoEnFacturacion("Carlos Núñez", "4775550109");
		vender(luis, sentra);

		assertThatThrownBy(() -> vender(carlos, sentra)).isInstanceOf(ConflictoException.class)
				.hasMessage("La unidad 3N1AB8CV5PY123456 ya no está disponible");
	}

	@Test
	void unaUnidadNoDisponibleNoSeVende() throws Exception {
		long danada = altaDeVehiculo("3N6AD33A2RK123459", "NO_DISPONIBLE");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		assertThatThrownBy(() -> vender(prospecto, danada)).isInstanceOf(ConflictoException.class);
		assertThat(jdbcTemplate.queryForObject("select count(*) from venta", Integer.class)).isZero();
	}

	@Test
	void unProspectoConVentaNoCompraOtraUnidad() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long kicks = altaDeVehiculo("3N1CP5CV6RL123458");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");
		vender(prospecto, sentra);

		assertThatThrownBy(() -> vender(prospecto, kicks)).isInstanceOf(ConflictoException.class)
				.hasMessage("El prospecto ya tiene una unidad vendida");
		assertThat(estatusDe(kicks)).isEqualTo("DISPONIBLE");
	}

	@Test
	void siNoExisteLaUnidadNoSeVende() throws Exception {
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		assertThatThrownBy(() -> vender(prospecto, 999_999L)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el vehículo 999999");
	}

	@Test
	void yaNoSeEligeLaUnidadAntesDeFacturar() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		mockMvc.perform(conToken(put("/api/v1/ventas/{id}/unidad", prospecto), token(facturacion))
				.contentType(MediaType.APPLICATION_JSON).content("{\"vehiculoId\":%d}".formatted(sentra)))
				.andExpect(status().isNotFound());
		assertThat(estatusDe(sentra)).isEqualTo("DISPONIBLE");
	}

	@Test
	void facturacionYElAdministradorConsultanLasVentas() throws Exception {
		Usuario admin = registrar("admin.ventas", Role.ADMINISTRADOR);

		mockMvc.perform(conToken(get("/api/v1/ventas"), token(admin))).andExpect(status().isOk());
		mockMvc.perform(conToken(get("/api/v1/ventas"), token(facturacion))).andExpect(status().isOk());
		mockMvc.perform(conToken(get("/api/v1/ventas"), token(vendedor))).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get("/api/v1/ventas"), token(almacen))).andExpect(status().isForbidden());
	}

	@Test
	void facturacionSoloVeLasUnidadesDisponiblesParaElegir() throws Exception {
		altaDeVehiculo("3N1AB8CV5PY123456");
		altaDeVehiculo("3N6AD33A2RK123459", "NO_DISPONIBLE");

		mockMvc.perform(conToken(get("/api/v1/vehiculos"), token(facturacion))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].vin").value("3N1AB8CV5PY123456"));
	}

	@Test
	void laConsultaDeUnidadesRegresaLaUnidadDelProspecto() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");
		long sinUnidad = prospectoEnFacturacion("Carlos Núñez", "4775550109");
		vender(prospecto, sentra);

		assertThat(consultaUnidades.unidadDelProspecto(prospecto)).contains(new ConsultaUnidades.UnidadDelProspecto(
				sentra, "3N1AB8CV5PY123456", "Sentra", 2026, "Advance", "Nissan"));
		assertThat(consultaUnidades.unidadDelProspecto(sinUnidad)).isEmpty();
	}

	private UnidadVendida vender(long prospectoId, long vehiculoId) {
		return ventaDeUnidades.vender(prospectoId, vehiculoId, facturacion.getId(), Instant.now());
	}

	private long prospectoEnFacturacion(String nombre, String telefono) throws Exception {
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/prospectos"), token(vendedor)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","telefono":"%s","correo":null}
								""".formatted(nombre, telefono)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
		entityManager.flush();
		jdbcTemplate.update("update prospecto set etapa = 'FACTURACION' where id = ?", id);
		entityManager.clear();
		return id;
	}

	private long altaDeVehiculo(String vin) throws Exception {
		return altaDeVehiculo(vin, "DISPONIBLE");
	}

	private long altaDeVehiculo(String vin, String estatus) throws Exception {
		String cuerpo = """
				{"vin":"%s","modelo":"Sentra","version":"Advance","anio":2026,"color":"Blanco","precio":489900.00,
				"estatus":"%s"}
				""".formatted(vin, estatus);
		String respuesta = mockMvc
				.perform(conToken(post("/api/v1/vehiculos"), token(almacen)).contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private String estatusDe(long vehiculoId) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, vehiculoId);
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
