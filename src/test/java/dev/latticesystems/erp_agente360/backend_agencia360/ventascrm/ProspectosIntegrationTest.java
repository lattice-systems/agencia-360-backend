package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Prueba prospectos de punta a punta (CRM-1.0, CRM-1.1 y la regla de
 * visibilidad de CRM-0.1): migración, seguridad, endpoints y base de datos real
 * (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class ProspectosIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/prospectos";

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
	void unVendedorRegistraUnProspectoEnPrimerContacto() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","telefono":"4771234567"}
				""")).andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.nombre").value("Ana López"))
				.andExpect(jsonPath("$.telefono").value("4771234567")).andExpect(jsonPath("$.correo").isEmpty())
				.andExpect(jsonPath("$.etapa").value("PRIMER_CONTACTO"))
				.andExpect(jsonPath("$.vendedorId").value(vendedor.getId()))
				.andExpect(jsonPath("$.creadoEn").isNotEmpty()).andExpect(jsonPath("$.contactoRepetido").value(false));
	}

	@Test
	void elRegistroGuardaElPrimerMovimientoDelHistorial() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		long id = idDe(mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","correo":"ana@ejemplo.com"}
				""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		List<Map<String, Object>> historial = historialDe(id);
		assertThat(historial).singleElement().satisfies(movimiento -> {
			assertThat(movimiento).containsEntry("etapa", "PRIMER_CONTACTO").containsEntry("origen", "CRM");
			assertThat(movimiento.get("fecha_hora")).isNotNull();
		});
	}

	@Test
	void rechazaUnProspectoSinTelefonoNiCorreo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López"}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Captura al menos un teléfono o un correo"));
	}

	@Test
	void rechazaUnProspectoSinNombre() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":" ","telefono":"4771234567"}
				""")).andExpect(status().isBadRequest());
	}

	@Test
	void rechazaUnCorreoMalEscrito() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","correo":"ana-sin-arroba"}
				""")).andExpect(status().isBadRequest());
	}

	@Test
	void registraUnTelefonoRepetidoDeOtroVendedorYSoloAvisaSinDecirDeQuien() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		crearProspecto(vendedor1, "Ana López", "4771234567", null);

		mockMvc.perform(registro(token(vendedor2), """
				{"nombre":"Ana López","telefono":"4771234567"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.contactoRepetido").value(true))
				.andExpect(jsonPath("$.vendedorId").value(vendedor2.getId()));

		// Cada vendedor conserva su propio prospecto.
		mockMvc.perform(conToken(get(URL), token(vendedor1))).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(conToken(get(URL), token(vendedor2))).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void avisaQueElCorreoYaExisteSinImportarMayusculas() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		crearProspecto(vendedor, "Ana López", null, "ana@ejemplo.com");

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana","correo":"ANA@Ejemplo.com"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.contactoRepetido").value(true));
	}

	@Test
	void rechazaUnTelefonoQueNoEsDeDiezDigitos() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","telefono":"477123"}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El teléfono debe tener 10 dígitos"));
	}

	@Test
	void soloVentasRegistraProspectos() throws Exception {
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);

		mockMvc.perform(registro(token(gerente), """
				{"nombre":"Ana López","telefono":"4771234567"}
				""")).andExpect(status().isForbidden());
	}

	@Test
	void exigeIniciarSesion() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void unVendedorSoloVeSusProspectos() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		crearProspecto(vendedor1, "Ana López", "4771234567", null);
		crearProspecto(vendedor2, "Luis Pérez", "4779876543", null);

		mockMvc.perform(conToken(get(URL), token(vendedor1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Ana López"));
	}

	@Test
	void elGerenteDeMarketingVeLosProspectosDeTodos() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		crearProspecto(vendedor1, "Ana López", "4771234567", null);
		crearProspecto(vendedor2, "Luis Pérez", "4779876543", null);

		mockMvc.perform(conToken(get(URL), token(gerente))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void contabilidadNoVeProspectosMientrasElPoNoDefinaSuFuncionEnElCrm() throws Exception {
		Usuario contador = registrar("conta1", Role.CONTABILIDAD);

		mockMvc.perform(conToken(get(URL), token(contador))).andExpect(status().isForbidden());
	}

	@Test
	void filtraLaListaPorEtapa() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long ana = crearProspecto(vendedor, "Ana López", "4771234567", null);
		crearProspecto(vendedor, "Luis Pérez", "4779876543", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), ana, "VISITA")).andExpect(status().isOk());

		mockMvc.perform(conToken(get(URL).param("etapa", "VISITA"), token(vendedor))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Ana López"));
	}

	@Test
	void rechazaUnFiltroConUnaEtapaQueNoExiste() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(conToken(get(URL).param("etapa", "GANADO"), token(vendedor))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Etapa no válida: GANADO"));
	}

	@Test
	void cambiarLaEtapaRegistraElMovimientoEnElHistorial() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "COTIZACION")).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("COTIZACION"));

		assertThat(historialDe(id)).extracting(movimiento -> movimiento.get("etapa")).containsExactly("PRIMER_CONTACTO",
				"COTIZACION");
	}

	@Test
	void alCerrarseGuardaLaUltimaEtapa() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "COTIZACION")).andExpect(status().isOk());

		mockMvc.perform(cierre(token(vendedor), id, "Compró en otra agencia")).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("CERRADO"))
				.andExpect(jsonPath("$.motivoCierre").value("Compró en otra agencia"))
				.andExpect(jsonPath("$.ultimaEtapa").value("COTIZACION"));

		entityManager.flush();
		assertThat(jdbcTemplate.queryForMap("select ultima_etapa, motivo_cierre from prospecto where id = ?", id))
				.containsEntry("ultima_etapa", "COTIZACION").containsEntry("motivo_cierre", "Compró en otra agencia");
	}

	@Test
	void noSePuedeCerrarSinMotivo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "CERRADO")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El motivo de cierre es obligatorio"));
		assertThat(historialDe(id)).hasSize(1);
	}

	@Test
	void unVendedorNoPuedeCambiarUnProspectoAjenoYRecibe404() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		long id = crearProspecto(vendedor1, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor2), id, "VISITA")).andExpect(status().isNotFound());
		assertThat(historialDe(id)).hasSize(1);
	}

	@Test
	void rechazaUnaEtapaQueNoExisteAlCambiar() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "GANADO")).andExpect(status().isBadRequest());
	}

	@Test
	void elGerenteDeMarketingNoCambiaEtapas() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(gerente), id, "VISITA")).andExpect(status().isForbidden());
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long crearProspecto(Usuario vendedor, String nombre, String telefono, String correo) throws Exception {
		String cuerpo = """
				{"nombre":"%s","telefono":%s,"correo":%s}
				""".formatted(nombre, json(telefono), json(correo));
		String respuesta = mockMvc.perform(registro(token(vendedor), cuerpo)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idDe(respuesta);
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> historialDe(long prospectoId) {
		entityManager.flush();
		return jdbcTemplate.queryForList(
				"select etapa, origen, fecha_hora from historial_etapa where prospecto_id = ? order by id",
				prospectoId);
	}

	private static MockHttpServletRequestBuilder registro(String token, String cuerpo) {
		return conToken(post(URL), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder cambioDeEtapa(String token, long id, String etapa) {
		return conToken(patch(URL + "/{id}/etapa", id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"" + etapa + "\"}");
	}

	private static MockHttpServletRequestBuilder cierre(String token, long id, String motivo) {
		return conToken(patch(URL + "/{id}/etapa", id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"CERRADO\",\"motivoCierre\":\"" + motivo + "\"}");
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static long idDe(String respuesta) {
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private static String json(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}
}
