package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

		mockMvc.perform(alta(token(almacen), cuerpo(" 3vwfe21c04m000001 "))).andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.LOCATION)).andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.vin").value("3VWFE21C04M000001")).andExpect(jsonPath("$.modelo").value("Jetta"))
				.andExpect(jsonPath("$.version").value("Comfortline")).andExpect(jsonPath("$.anio").value(2026))
				.andExpect(jsonPath("$.color").value("Blanco")).andExpect(jsonPath("$.estatus").value("DISPONIBLE"))
				.andExpect(jsonPath("$.precio").value(389900.00)).andExpect(jsonPath("$.creadoEn").isNotEmpty());
	}

	@Test
	void unaUnidadPuedeEntrarComoNoDisponible() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpoConEstatus("3VWFE21C04M000001", "NO_DISPONIBLE")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estatus").value("NO_DISPONIBLE"));
	}

	@Test
	void unaUnidadNoPuedeEntrarConUnEstatusDeVenta() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpoConEstatus("3VWFE21C04M000001", "VENDIDO")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Una unidad solo puede entrar como Disponible o No disponible"));
		mockMvc.perform(alta(token(almacen), cuerpoConEstatus("3VWFE21C04M000001", "EN_TRANSITO")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void elAltaRegistraLaEntradaAlInventarioConSuResponsable() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		long id = altaDe(almacen, "3VWFE21C04M000001");

		assertThat(movimientosDe(id)).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.get("tipo")).isEqualTo("ENTRADA");
			assertThat(((Number) movimiento.get("cantidad")).intValue()).isEqualTo(1);
			assertThat(((Number) movimiento.get("responsable_id")).longValue()).isEqualTo(almacen.getId());
			assertThat(movimiento.get("fecha_registro")).isNotNull();
			assertThat(movimiento.get("fecha_llegada")).isEqualTo(movimiento.get("fecha_registro"));
		});
	}

	@Test
	void rechazaUnVinRepetidoCon409() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(alta(token(almacen), cuerpo("3vwfe21c04m000001"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Ya existe un vehículo con el VIN 3VWFE21C04M000001"));
	}

	@Test
	void rechazaUnVinInvalidoCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpo("3VWFE21C04M00000O"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El VIN debe tener 17 caracteres, sin las letras I, O ni Q"));
	}

	@Test
	void rechazaUnPrecioInvalidoCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), """
				{"vin":"3VWFE21C04M000001","modelo":"Jetta","version":"Comfortline","anio":2026,"color":"Blanco",
				"precio":0}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El precio debe ser mayor a cero"));
		mockMvc.perform(alta(token(almacen), """
				{"vin":"3VWFE21C04M000001","modelo":"Jetta","version":"Comfortline","anio":2026,"color":"Blanco",
				"precio":1000.555}
				""")).andExpect(status().isBadRequest());
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
	void soloInventarioDaDeAltaUnidades() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario administrador = registrar("admin1", Role.ADMINISTRADOR);

		mockMvc.perform(alta(token(vendedor), cuerpo("3VWFE21C04M000001"))).andExpect(status().isForbidden());
		mockMvc.perform(alta(token(administrador), cuerpo("3VWFE21C04M000001"))).andExpect(status().isForbidden());
	}

	@Test
	void otrosRolesNoConsultanUnidades() throws Exception {
		Usuario cajero = registrar("caja1", Role.CAJA);
		Usuario refacciones = registrar("refacciones1", Role.REFACCIONES);

		mockMvc.perform(conToken(get(URL), token(cajero))).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), token(refacciones))).andExpect(status().isForbidden());
	}

	@Test
	void elAdministradorVeTodasLasUnidades() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		Usuario administrador = registrar("admin1", Role.ADMINISTRADOR);
		altaDe(almacen, "3VWFE21C04M000001");
		long noDisponible = altaNoDisponible(almacen, "3VWFE21C04M000002");

		mockMvc.perform(conToken(get(URL), token(administrador))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(conToken(get(URL + "/{id}", noDisponible), token(administrador))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estatus").value("NO_DISPONIBLE"));
	}

	@Test
	void ventasSoloVeLasUnidadesDisponibles() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long disponible = altaDe(almacen, "3VWFE21C04M000001");
		long noDisponible = altaNoDisponible(almacen, "3VWFE21C04M000002");

		mockMvc.perform(conToken(get(URL), token(vendedor))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(disponible));
		mockMvc.perform(conToken(get(URL).param("estatus", "NO_DISPONIBLE"), token(vendedor)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(conToken(get(URL + "/{id}", disponible), token(vendedor))).andExpect(status().isOk());
		mockMvc.perform(conToken(get(URL + "/{id}", noDisponible), token(vendedor))).andExpect(status().isNotFound());
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

	@Test
	void inventarioCorrigeLosDatosDeUnaUnidad() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(edicion(token(almacen), id, """
				{"vin":"3vwfe21c04m000009","modelo":"Jetta","version":"GLI","anio":2025,"color":"Rojo",
				"precio":409900.50,
				"estatus":"NO_DISPONIBLE"}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.vin").value("3VWFE21C04M000009"))
				.andExpect(jsonPath("$.version").value("GLI")).andExpect(jsonPath("$.anio").value(2025))
				.andExpect(jsonPath("$.estatus").value("NO_DISPONIBLE"))
				.andExpect(jsonPath("$.precio").value(409900.50));

		mockMvc.perform(conToken(get(URL + "/{id}", id), token(almacen))).andExpect(jsonPath("$.color").value("Rojo"));
	}

	@Test
	void editarConElVinDeOtraUnidadResponde409() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		altaDe(almacen, "3VWFE21C04M000001");
		long id = altaDe(almacen, "3VWFE21C04M000002");

		mockMvc.perform(edicion(token(almacen), id, cuerpo("3VWFE21C04M000001"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Ya existe un vehículo con el VIN 3VWFE21C04M000001"));
	}

	@Test
	void editarConservaElVinPropioSinConflicto() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(edicion(token(almacen), id, cuerpo("3VWFE21C04M000001"))).andExpect(status().isOk());
	}

	@Test
	void inventarioNoPoneEstatusDeVentaAlEditar() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(edicion(token(almacen), id, cuerpoConEstatus("3VWFE21C04M000001", "VENDIDO")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message")
						.value("Inventario solo cambia el estatus entre Disponible y No disponible"));
	}

	@Test
	void unaUnidadApartadaSeCorrigePeroSuEstatusLoCambiaVentas() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = altaDe(almacen, "3VWFE21C04M000001");
		entityManager.flush();
		jdbcTemplate.update("update vehiculo set estatus = 'APARTADA' where id = ?", id);
		entityManager.clear();

		mockMvc.perform(edicion(token(almacen), id, cuerpoConEstatus("3VWFE21C04M000001", "DISPONIBLE")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message")
						.value("Inventario solo cambia el estatus entre Disponible y No disponible"));
		mockMvc.perform(edicion(token(almacen), id, cuerpo("3VWFE21C04M000001"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estatus").value("APARTADA"));
	}

	@Test
	void soloInventarioEditaYNadaSeElimina() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		Usuario administrador = registrar("admin1", Role.ADMINISTRADOR);
		long id = altaDe(almacen, "3VWFE21C04M000001");

		mockMvc.perform(edicion(token(administrador), id, cuerpo("3VWFE21C04M000001")))
				.andExpect(status().isForbidden());
		mockMvc.perform(conToken(delete(URL + "/{id}", id), token(almacen))).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void editarUnaUnidadQueNoExisteResponde404() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(edicion(token(almacen), 999_999, cuerpo("3VWFE21C04M000001"))).andExpect(status().isNotFound());
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long altaDe(Usuario usuario, String vin) throws Exception {
		String respuesta = mockMvc.perform(alta(token(usuario), cuerpo(vin))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private long altaNoDisponible(Usuario usuario, String vin) throws Exception {
		String respuesta = mockMvc.perform(alta(token(usuario), cuerpoConEstatus(vin, "NO_DISPONIBLE")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> movimientosDe(long vehiculoId) {
		entityManager.flush();
		return jdbcTemplate.queryForList("select tipo, cantidad, responsable_id, fecha_llegada, fecha_registro "
				+ "from movimiento_inventario where vehiculo_id = ?", vehiculoId);
	}

	private static String cuerpo(String vin) {
		return """
				{"vin":"%s","modelo":"Jetta","version":"Comfortline","anio":2026,"color":"Blanco","precio":389900.00}
				""".formatted(vin);
	}

	private static String cuerpoConEstatus(String vin, String estatus) {
		return """
				{"vin":"%s","modelo":"Jetta","version":"Comfortline","anio":2026,"color":"Blanco","precio":389900.00,
				"estatus":"%s"}
				""".formatted(vin, estatus);
	}

	private static MockHttpServletRequestBuilder alta(String token, String cuerpo) {
		return conToken(post(URL), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder edicion(String token, long id, String cuerpo) {
		return conToken(put(URL + "/{id}", id), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
