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
 * Prueba el alta y la consulta de refacciones (ERP-2.0) de punta a punta:
 * seguridad, endpoints y base de datos real (Testcontainers). Cada prueba se
 * revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class RefaccionesIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/refacciones";

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
	void inventarioDaDeAltaUnaRefaccion() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpo(" fil-ace-01 ", "12"))).andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.LOCATION)).andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.codigo").value("FIL-ACE-01"))
				.andExpect(jsonPath("$.descripcion").value("Filtro de aceite"))
				.andExpect(jsonPath("$.costo").value(180.50)).andExpect(jsonPath("$.precio").value(320.00))
				.andExpect(jsonPath("$.existencia").value(12)).andExpect(jsonPath("$.minimo").value(5))
				.andExpect(jsonPath("$.creadoEn").isNotEmpty());
	}

	@Test
	void laExistenciaInicialQuedaComoEntradaConSuResponsable() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		long id = altaDe(almacen, "FIL-ACE-01", "12");

		assertThat(movimientosDe(id)).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.get("tipo")).isEqualTo("ENTRADA");
			assertThat(((Number) movimiento.get("cantidad")).intValue()).isEqualTo(12);
			assertThat(((Number) movimiento.get("responsable_id")).longValue()).isEqualTo(almacen.getId());
		});
	}

	@Test
	void sinExistenciaInicialEmpiezaEnCeroSinMovimientos() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), """
				{"codigo":"FIL-ACE-01","descripcion":"Filtro de aceite","costo":180.50,"precio":320.00,"minimo":5}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.existencia").value(0));
		long id = jdbcTemplate.queryForObject("select id from refaccion where codigo = 'FIL-ACE-01'", Long.class);
		assertThat(movimientosDe(id)).isEmpty();
	}

	@Test
	void rechazaUnCodigoRepetidoCon409() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		altaDe(almacen, "FIL-ACE-01", "0");

		mockMvc.perform(alta(token(almacen), cuerpo("fil-ace-01", "0"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Ya existe una refacción con el código FIL-ACE-01"));
	}

	@Test
	void rechazaDatosInvalidosCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(alta(token(almacen), cuerpo("FIL-ACE-01", "-1"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La existencia no puede ser negativa"));
		mockMvc.perform(alta(token(almacen), """
				{"codigo":"FIL-ACE-01","descripcion":"Filtro de aceite","costo":-1,"precio":320.00,"minimo":5}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El costo no puede ser negativo"));
		mockMvc.perform(alta(token(almacen), """
				{"codigo":"FIL-ACE-01","descripcion":"Filtro de aceite","costo":180.50,"precio":320.00}
				""")).andExpect(status().isBadRequest());
	}

	@Test
	void sinSesionResponde401() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void otrosRolesNoDanDeAltaNiConsultanRefacciones() throws Exception {
		Usuario refacciones = registrar("refacciones1", Role.REFACCIONES);
		Usuario cajero = registrar("caja1", Role.CAJA);

		mockMvc.perform(alta(token(refacciones), cuerpo("FIL-ACE-01", "0"))).andExpect(status().isForbidden());
		mockMvc.perform(conToken(get(URL), token(cajero))).andExpect(status().isForbidden());
	}

	@Test
	void listaLasRefaccionesOrdenadasPorCodigo() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		altaDe(almacen, "FIL-ACE-01", "3");
		altaDe(almacen, "BAL-DEL-01", "0");

		mockMvc.perform(conToken(get(URL), token(almacen))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].codigo").value("BAL-DEL-01"))
				.andExpect(jsonPath("$[1].codigo").value("FIL-ACE-01")).andExpect(jsonPath("$[1].existencia").value(3));
	}

	@Test
	void consultaUnaRefaccionPorId() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = altaDe(almacen, "FIL-ACE-01", "4");

		mockMvc.perform(conToken(get(URL + "/{id}", id), token(almacen))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.existencia").value(4));
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() throws Exception {
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

	private long altaDe(Usuario usuario, String codigo, String existencia) throws Exception {
		String respuesta = mockMvc.perform(alta(token(usuario), cuerpo(codigo, existencia)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> movimientosDe(long refaccionId) {
		entityManager.flush();
		return jdbcTemplate.queryForList(
				"select tipo, cantidad, responsable_id, fecha from movimiento_inventario where refaccion_id = ?",
				refaccionId);
	}

	private static String cuerpo(String codigo, String existencia) {
		return """
				{"codigo":"%s","descripcion":"Filtro de aceite","costo":180.50,"precio":320.00,"existencia":%s,"minimo":5}
				"""
				.formatted(codigo, existencia);
	}

	private static MockHttpServletRequestBuilder alta(String token, String cuerpo) {
		return conToken(post(URL), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
