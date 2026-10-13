package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.Map;
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
 * Prueba el alta y la consulta de vehículos (ERP-2.0) de punta a punta:
 * seguridad, endpoints y base de datos real (Testcontainers). Cada prueba se
 * revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class VehiculosIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/vehiculos";

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

	@Test
	void inventarioDaDeAltaUnaUnidadDisponible() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpo(" 3vwfe21c04m000001 ", "389900.00")))
				.andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.vin").value("3VWFE21C04M000001"))
				.andExpect(jsonPath("$.modelo").value("Jetta")).andExpect(jsonPath("$.version").value("Comfortline"))
				.andExpect(jsonPath("$.anio").value(2026)).andExpect(jsonPath("$.color").value("Blanco"))
				.andExpect(jsonPath("$.precio").value(389900.00)).andExpect(jsonPath("$.estatus").value("DISPONIBLE"))
				.andExpect(jsonPath("$.creadoEn").isNotEmpty());
	}

	@Test
	void elAltaRegistraLaEntradaAlInventarioConSuResponsable() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		long id = altaDe(almacen, "3VWFE21C04M000001");

		assertThat(movimientosDe(id)).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.get("tipo")).isEqualTo("ENTRADA");
			assertThat(((Number) movimiento.get("cantidad")).intValue()).isEqualTo(1);
			assertThat(((Number) movimiento.get("responsable_id")).longValue()).isEqualTo(almacen.getId());
			assertThat(movimiento.get("fecha")).isNotNull();
		});
	}

	@Test
	void rechazaUnVinRepetidoCon409() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(alta(token(almacen), cuerpo("3vwfe21c04m000001", "389900.00"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Ya existe un vehículo con el VIN 3VWFE21C04M000001"));
	}

	@Test
	void rechazaUnVinInvalidoCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpo("3VWFE21C04M00000O", "389900.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El VIN debe tener 17 caracteres, sin las letras I, O ni Q"));
	}

	@Test
	void rechazaUnPrecioInvalidoCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpo("3VWFE21C04M000001", "0"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El precio debe ser mayor a cero"));
		mockMvc.perform(alta(token(almacen), cuerpo("3VWFE21C04M000001", "1000.555")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rechazaUnAltaSinDatosObligatoriosCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), """
				{"vin":"3VWFE21C04M000001","modelo":"Jetta"}
				""")).andExpect(status().isBadRequest());
	}

	@Test
	void sinSesionResponde401() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void otrosRolesNoDanDeAltaNiConsultanVehiculos() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario cajero = registrar("caja1", Role.CAJA);

		mockMvc.perform(alta(token(vendedor), cuerpo("3VWFE21C04M000001", "389900.00")))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), token(cajero))).andExpect(status().isForbidden());
	}

	@Test
	void listaLasUnidadesDeLaMasRecienteALaMasAntigua() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		altaDe(almacen, "3VWFE21C04M000001");
		altaDe(almacen, "3VWFE21C04M000002");

		mockMvc.perform(conToken(get(URL), token(almacen))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].vin").value("3VWFE21C04M000002"))
				.andExpect(jsonPath("$[1].vin").value("3VWFE21C04M000001"));
	}

	@Test
	void filtraLasUnidadesPorEstatus() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long apartada = altaDe(almacen, "3VWFE21C04M000001");
		altaDe(almacen, "3VWFE21C04M000002");
		entityManager.flush();
		jdbcTemplate.update("update vehiculo set estatus = 'APARTADA' where id = ?", apartada);
		entityManager.clear();

		mockMvc.perform(conToken(get(URL).param("estatus", "DISPONIBLE"), token(almacen))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].vin").value("3VWFE21C04M000002"));
		mockMvc.perform(conToken(get(URL).param("estatus", "APARTADA"), token(almacen))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(apartada));
	}

	@Test
	void rechazaUnEstatusQueNoExisteCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(conToken(get(URL).param("estatus", "EN_TRANSITO"), token(almacen)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Estatus no válido: EN_TRANSITO"));
	}

	@Test
	void consultaUnaUnidadPorId() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(conToken(get(URL + "/{id}", id), token(almacen))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.vin").value("3VWFE21C04M000001"));
	}

	@Test
	void unaUnidadQueNoExisteResponde404() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(conToken(get(URL + "/{id}", 999_999), token(almacen))).andExpect(status().isNotFound());
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long altaDe(Usuario usuario, String vin) throws Exception {
		String respuesta = mockMvc.perform(alta(token(usuario), cuerpo(vin, "389900.00")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> movimientosDe(long vehiculoId) {
		entityManager.flush();
		return jdbcTemplate.queryForList(
				"select tipo, cantidad, responsable_id, fecha from movimiento_inventario where vehiculo_id = ?",
				vehiculoId);
	}

	private static String cuerpo(String vin, String precio) {
		return """
				{"vin":"%s","modelo":"Jetta","version":"Comfortline","anio":2026,"color":"Blanco","precio":%s}
				""".formatted(vin, precio);
	}

	private static MockHttpServletRequestBuilder alta(String token, String cuerpo) {
		return conToken(post(URL), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
