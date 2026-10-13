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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * endpoints y base de datos real (Testcontainers). Cada prueba se revierte al
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
		long id = refaccion(almacen);
		recibir(almacen, id, 3);

		mockMvc.perform(recepcion(token(almacen), id, """
				{"cantidad":10,"proveedor":"Refacciones del Bajío","notas":"Una caja golpeada"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.movimientoId").isNumber())
				.andExpect(jsonPath("$.refaccionId").value(id)).andExpect(jsonPath("$.codigo").value("FIL-ACE-01"))
				.andExpect(jsonPath("$.cantidad").value(10))
				.andExpect(jsonPath("$.proveedor").value("Refacciones del Bajío"))
				.andExpect(jsonPath("$.notas").value("Una caja golpeada"))
				.andExpect(jsonPath("$.responsableId").value(almacen.getId()))
				.andExpect(jsonPath("$.fechaLlegada").isNotEmpty()).andExpect(jsonPath("$.fechaRegistro").isNotEmpty())
				.andExpect(jsonPath("$.existencia").value(13));

		mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}", id), token(almacen)))
				.andExpect(jsonPath("$.existencia").value(13));
	}

	@Test
	void sinFechaDeLlegadaLasDosFechasCoinciden() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccion(almacen);

		recibir(almacen, id, 7);

		assertThat(movimientosDe(id)).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.get("tipo")).isEqualTo("ENTRADA");
			assertThat(((Number) movimiento.get("cantidad")).intValue()).isEqualTo(7);
			assertThat(((Number) movimiento.get("responsable_id")).longValue()).isEqualTo(almacen.getId());
			assertThat(movimiento.get("proveedor")).isEqualTo("Refacciones del Bajío");
			assertThat(movimiento.get("notas")).isNull();
			assertThat(movimiento.get("fecha_llegada")).isEqualTo(movimiento.get("fecha_registro"));
		});
	}

	@Test
	void guardaUnaLlegadaAnteriorAlRegistro() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccion(almacen);
		Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

		mockMvc.perform(recepcion(token(almacen), id, """
				{"cantidad":2,"proveedor":"Bajío","fechaLlegada":"%s"}
				""".formatted(ayer))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.fechaLlegada").value(ayer.toString()));

		mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}/recepciones", id), token(almacen)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].fechaLlegada").value(ayer.toString()))
				.andExpect(jsonPath("$[0].fechaRegistro").isNotEmpty());
	}

	@Test
	void rechazaUnaLlegadaFuturaCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccion(almacen);
		Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);

		mockMvc.perform(recepcion(token(almacen), id, """
				{"cantidad":2,"proveedor":"Bajío","fechaLlegada":"%s"}
				""".formatted(manana))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La fecha de llegada no puede ser posterior al registro"));
		assertThat(movimientosDe(id)).isEmpty();
	}

	@Test
	void cadaRecepcionQuedaEnElHistorialDeLaMasRecienteALaMasAntigua() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccion(almacen);
		recibir(almacen, id, 5);
		recibir(almacen, id, 1);

		mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}/recepciones", id), token(almacen)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].cantidad").value(1)).andExpect(jsonPath("$[1].cantidad").value(5))
				.andExpect(jsonPath("$[0].proveedor").value("Refacciones del Bajío"))
				.andExpect(jsonPath("$[0].responsableId").value(almacen.getId()));
	}

	@Test
	void rechazaDatosInvalidosCon400() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		long id = refaccion(almacen);

		mockMvc.perform(recepcion(token(almacen), id, """
				{"cantidad":0,"proveedor":"Bajío"}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La cantidad debe ser mayor a cero"));
		mockMvc.perform(recepcion(token(almacen), id, """
				{"cantidad":3}
				""")).andExpect(status().isBadRequest());
		mockMvc.perform(recepcion(token(almacen), id, "{}")).andExpect(status().isBadRequest());
		assertThat(movimientosDe(id)).isEmpty();
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);

		mockMvc.perform(recepcion(token(almacen), 999_999, """
				{"cantidad":1,"proveedor":"Bajío"}
				""")).andExpect(status().isNotFound());
		mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}/recepciones", 999_999), token(almacen)))
				.andExpect(status().isNotFound());
	}

	@Test
	void sinSesionResponde401() throws Exception {
		mockMvc.perform(post(URL_REFACCIONES + "/{id}/recepciones", 1).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":1,\"proveedor\":\"Bajío\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void soloInventarioRegistraRecepciones() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		Usuario refacciones = registrar("refacciones1", Role.REFACCIONES);
		Usuario administrador = registrar("admin1", Role.ADMINISTRADOR);
		long id = refaccion(almacen);

		for (Usuario usuario : List.of(refacciones, administrador)) {
			mockMvc.perform(recepcion(token(usuario), id, """
					{"cantidad":1,"proveedor":"Bajío"}
					""")).andExpect(status().isForbidden());
		}
		assertThat(movimientosDe(id)).isEmpty();
	}

	@Test
	void refaccionesYAdministradorConsultanElHistorial() throws Exception {
		Usuario almacen = registrar("almacen1", Role.INVENTARIO);
		Usuario refacciones = registrar("refacciones1", Role.REFACCIONES);
		Usuario administrador = registrar("admin1", Role.ADMINISTRADOR);
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = refaccion(almacen);
		recibir(almacen, id, 4);

		for (Usuario usuario : List.of(refacciones, administrador)) {
			mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}/recepciones", id), token(usuario)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		}
		mockMvc.perform(conToken(get(URL_REFACCIONES + "/{id}/recepciones", id), token(vendedor)))
				.andExpect(status().isForbidden());
	}
	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long refaccion(Usuario usuario) throws Exception {
		String respuesta = mockMvc.perform(
				conToken(post(URL_REFACCIONES), token(usuario)).contentType(MediaType.APPLICATION_JSON).content("""
						{"codigo":"FIL-ACE-01","descripcion":"Filtro de aceite","costo":180.50,
						"precio":320.00,"minimo":5}
						""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private void recibir(Usuario usuario, long refaccionId, int cantidad) throws Exception {
		mockMvc.perform(recepcion(token(usuario), refaccionId, """
				{"cantidad":%d,"proveedor":"Refacciones del Bajío"}
				""".formatted(cantidad))).andExpect(status().isCreated());
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> movimientosDe(long refaccionId) {
		entityManager.flush();
		return jdbcTemplate.queryForList("select tipo, cantidad, responsable_id, proveedor, notas, fecha_llegada, "
				+ "fecha_registro from movimiento_inventario where refaccion_id = ? order by id", refaccionId);
	}

	private static MockHttpServletRequestBuilder recepcion(String token, long id, String cuerpo) {
		return conToken(post(URL_REFACCIONES + "/{id}/recepciones", id), token).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo);
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
