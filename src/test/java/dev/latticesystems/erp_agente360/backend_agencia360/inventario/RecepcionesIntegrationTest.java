package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Prueba la recepción de refacciones (ERP-2.0) de punta a punta: seguridad,
 * endpoint y base de datos real (Testcontainers). Cada prueba se revierte al
 * terminar.
 */
@AutoConfigureMockMvc
@Transactional
class RecepcionesIntegrationTest extends AbstractIntegrationTest {

	private static final String URL_REFACCIONES = "/api/v1/refacciones";

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
	void laRecepcionSumaALaExistenciaYResponde201() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccionCon(almacen, 3);

		mockMvc.perform(recepcion(token(almacen), id, "{\"cantidad\":10}")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.movimientoId").isNumber()).andExpect(jsonPath("$.refaccionId").value(id))
				.andExpect(jsonPath("$.codigo").value("FIL-ACE-01")).andExpect(jsonPath("$.cantidad").value(10))
				.andExpect(jsonPath("$.responsableId").value(almacen.getId()))
				.andExpect(jsonPath("$.fecha").isNotEmpty()).andExpect(jsonPath("$.existencia").value(13));

		mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}", id), token(almacen)))
				.andExpect(jsonPath("$.existencia").value(13));
	}

	@Test
	void guardaLaFechaLaCantidadYElResponsable() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccionCon(almacen, 0);

		mockMvc.perform(recepcion(token(almacen), id, "{\"cantidad\":7}")).andExpect(status().isCreated());

		assertThat(movimientosDe(id)).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.get("tipo")).isEqualTo("ENTRADA");
			assertThat(((Number) movimiento.get("cantidad")).intValue()).isEqualTo(7);
			assertThat(((Number) movimiento.get("responsable_id")).longValue()).isEqualTo(almacen.getId());
			assertThat(movimiento.get("fecha")).isNotNull();
		});
	}

	@Test
	void cadaRecepcionQuedaComoUnMovimientoAparte() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccionCon(almacen, 2);

		mockMvc.perform(recepcion(token(almacen), id, "{\"cantidad\":5}")).andExpect(status().isCreated());
		mockMvc.perform(recepcion(token(almacen), id, "{\"cantidad\":1}")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.existencia").value(8));

		assertThat(movimientosDe(id)).extracting(movimiento -> ((Number) movimiento.get("cantidad")).intValue())
				.containsExactly(2, 5, 1);
	}

	@Test
	void rechazaUnaCantidadInvalidaCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccionCon(almacen, 3);

		mockMvc.perform(recepcion(token(almacen), id, "{\"cantidad\":0}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La cantidad debe ser mayor a cero"));
		mockMvc.perform(recepcion(token(almacen), id, "{}")).andExpect(status().isBadRequest());
		assertThat(movimientosDe(id)).hasSize(1);
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(recepcion(token(almacen), 999_999, "{\"cantidad\":1}")).andExpect(status().isNotFound());
	}

	@Test
	void sinSesionResponde401() throws Exception {
		mockMvc.perform(post(URL_REFACCIONES + "/{id}/recepciones", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":1}")).andExpect(status().isUnauthorized());
	}

	@Test
	void otrosRolesNoRegistranRecepciones() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		Usuario refacciones = registrar("refacciones1", Role.REFACCIONES);
		long id = refaccionCon(almacen, 3);

		mockMvc.perform(recepcion(token(refacciones), id, "{\"cantidad\":1}")).andExpect(status().isForbidden());
		assertThat(movimientosDe(id)).hasSize(1);
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long refaccionCon(Usuario usuario, int existencia) throws Exception {
		String respuesta = mockMvc
				.perform(
						conToken(post(URL_REFACCIONES), token(usuario)).contentType(MediaType.APPLICATION_JSON).content(
								"""
										{"codigo":"FIL-ACE-01","descripcion":"Filtro de aceite","costo":180.50,"precio":320.00,"existencia":%d,"minimo":5}
										"""
										.formatted(existencia)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> movimientosDe(long refaccionId) {
		entityManager.flush();
		return jdbcTemplate.queryForList(
				"select tipo, cantidad, responsable_id, fecha from movimiento_inventario where refaccion_id = ? order by id",
				refaccionId);
	}

	private static MockHttpServletRequestBuilder recepcion(String token, long id, String cuerpo) {
		return conToken(post(URL_REFACCIONES + "/{id}/recepciones", id), token).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo);
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
