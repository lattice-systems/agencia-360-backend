package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
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
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import jakarta.persistence.EntityManager;
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
 * Venta de la unidad (ERP-1.0) de punta a punta: Facturación elige la unidad de
 * un prospecto y la unidad pasa a Vendido; la consulta de unidades la regresa a
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
	void facturacionEligeUnaUnidadDisponibleYPasaAVendido() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		mockMvc.perform(asignar(token(facturacion), prospecto, sentra)).andExpect(status().isOk())
				.andExpect(jsonPath("$.prospectoId").value(prospecto)).andExpect(jsonPath("$.vehiculoId").value(sentra))
				.andExpect(jsonPath("$.vin").value("3N1AB8CV5PY123456")).andExpect(jsonPath("$.modelo").value("Sentra"))
				.andExpect(jsonPath("$.precio").value(489900.00)).andExpect(jsonPath("$.estatus").value("POR_ENTREGAR"))
				.andExpect(jsonPath("$.asignadaEn").isNotEmpty());

		assertThat(estatusDe(sentra)).isEqualTo("VENDIDO");
		mockMvc.perform(conToken(get("/api/v1/ventas"), token(facturacion))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].prospectoId").value(prospecto));
	}

	@Test
	void alCambiarDeUnidadLaAnteriorRegresaADisponible() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long kicks = altaDeVehiculo("3N1CP5CV6RL123458");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");
		mockMvc.perform(asignar(token(facturacion), prospecto, sentra)).andExpect(status().isOk());

		mockMvc.perform(asignar(token(facturacion), prospecto, kicks)).andExpect(status().isOk())
				.andExpect(jsonPath("$.vehiculoId").value(kicks));

		assertThat(estatusDe(kicks)).isEqualTo("VENDIDO");
		assertThat(estatusDe(sentra)).isEqualTo("DISPONIBLE");
		assertThat(jdbcTemplate.queryForObject("select count(*) from venta where prospecto_id = ?", Integer.class,
				prospecto)).isEqualTo(1);
	}

	@Test
	void unaUnidadVendidaNoSeEligeParaOtroProspecto() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long luis = prospectoEnFacturacion("Luis Herrera", "4775550104");
		long carlos = prospectoEnFacturacion("Carlos Núñez", "4775550109");
		mockMvc.perform(asignar(token(facturacion), luis, sentra)).andExpect(status().isOk());

		mockMvc.perform(asignar(token(facturacion), carlos, sentra)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("La unidad 3N1AB8CV5PY123456 ya no está disponible"));
	}

	@Test
	void unaUnidadNoDisponibleNoSeElige() throws Exception {
		long danada = altaDeVehiculo("3N6AD33A2RK123459", "NO_DISPONIBLE");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		mockMvc.perform(asignar(token(facturacion), prospecto, danada)).andExpect(status().isConflict());
		assertThat(jdbcTemplate.queryForObject("select count(*) from venta", Integer.class)).isZero();
	}

	@Test
	void respondeNoEncontradoSiNoExisteLaUnidadOElProspecto() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		mockMvc.perform(asignar(token(facturacion), prospecto, 999_999)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el vehículo 999999"));
		mockMvc.perform(asignar(token(facturacion), 999_999, sentra)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el prospecto 999999"));
		assertThat(estatusDe(sentra)).isEqualTo("DISPONIBLE");
	}

	@Test
	void pideLaUnidad() throws Exception {
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");

		mockMvc.perform(conToken(put("/api/v1/ventas/{id}/unidad", prospecto), token(facturacion))
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	void soloFacturacionEligeLaUnidad() throws Exception {
		long sentra = altaDeVehiculo("3N1AB8CV5PY123456");
		long prospecto = prospectoEnFacturacion("Luis Herrera", "4775550104");
		Usuario admin = registrar("admin.ventas", Role.ADMINISTRADOR);

		mockMvc.perform(asignar(token(almacen), prospecto, sentra)).andExpect(status().isForbidden());
		mockMvc.perform(asignar(token(admin), prospecto, sentra)).andExpect(status().isForbidden());
		mockMvc.perform(asignar(token(vendedor), prospecto, sentra)).andExpect(status().isForbidden());
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
		mockMvc.perform(asignar(token(facturacion), prospecto, sentra)).andExpect(status().isOk());

		assertThat(consultaUnidades.unidadDelProspecto(prospecto)).contains(new ConsultaUnidades.UnidadDelProspecto(
				sentra, "3N1AB8CV5PY123456", "Sentra", 2026, "Advance", "Nissan"));
		assertThat(consultaUnidades.unidadDelProspecto(sinUnidad)).isEmpty();
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

	private static MockHttpServletRequestBuilder asignar(String token, long prospectoId, long vehiculoId) {
		return conToken(put("/api/v1/ventas/{id}/unidad", prospectoId), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":%d}".formatted(vehiculoId));
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
