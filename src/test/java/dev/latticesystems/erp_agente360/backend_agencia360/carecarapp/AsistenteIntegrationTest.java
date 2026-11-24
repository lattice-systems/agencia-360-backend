package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Asistente CareCar (V2-M24.02 y V2-M24.03) de punta a punta: PDF real de tres
 * páginas, indexado por página, búsqueda por texto completo, proveedor falso
 * (ADR 0007), límites y errores. Sin {@code @Transactional}: el indexado corre
 * en su propia transacción después de confirmar la subida, así que cada prueba
 * usa datos únicos.
 */
@AutoConfigureMockMvc
class AsistenteIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String modelo;
	private String token;
	private Long vehiculoId;

	@BeforeEach
	void clienteConSuVehiculo() {
		String unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
		modelo = "Prueba" + unico;
		String correo = "asistente-" + unico.toLowerCase() + "@correo.com";
		Long cliente = registroClientesService.registrar(new NuevoCliente("Ana Asistente", correo, null, null)).id();
		vehiculoId = registroClientesService
				.asignarVehiculo(cliente,
						new VehiculoNuevo(null, "3N1AS" + unico + "0001", "Nissan", modelo, 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(cliente, "Temporal123");
		token = jwtService.generateToken(cliente, correo, List.of(Role.CLIENTE_CARECAR));
	}

	@Test
	void respondeConBaseEnElManualYCitaLaPagina() throws Exception {
		subirManual(modelo).andExpect(status().isOk());

		preguntar(vehiculoId, "¿Cada cuánto cambio el aceite del motor?").andExpect(status().isOk())
				.andExpect(jsonPath("$.encontrado").value(true)).andExpect(jsonPath("$.fuentes.length()").value(1))
				.andExpect(jsonPath("$.fuentes[0].pagina").value(2))
				.andExpect(jsonPath("$.fuentes[0].titulo").value("Manual, pág. 2"))
				.andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.containsString("10 000 km")))
				.andExpect(jsonPath("$.consultaId").isNumber());
	}

	@Test
	void siElManualNoLoDiceRespondeQueNoLoEncontroSinFuentes() throws Exception {
		subirManual(modelo).andExpect(status().isOk());

		preguntar(vehiculoId, "¿Tiene techo panorámico?").andExpect(status().isOk())
				.andExpect(jsonPath("$.encontrado").value(false)).andExpect(jsonPath("$.fuentes.length()").value(0))
				.andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.containsString("No encontré")));
	}

	@Test
	void sinManualResponde409YUnVehiculoAjenoResponde404() throws Exception {
		preguntar(vehiculoId, "¿Cada cuánto cambio el aceite?").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ASISTENTE_SIN_MANUAL"));

		preguntar(Long.MAX_VALUE, "¿Cada cuánto cambio el aceite?").andExpect(status().isNotFound());
	}

	@Test
	void laPreguntaSeGuardaSinDatosPersonales() throws Exception {
		subirManual(modelo).andExpect(status().isOk());

		String respuesta = preguntar(vehiculoId, "Soy ana@correo.com, ¿cada cuánto cambio el aceite?")
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Integer consultaId = com.jayway.jsonpath.JsonPath.read(respuesta, "$.consultaId");

		String guardada = jdbc.queryForObject("select pregunta from consulta_asistente where id = ?", String.class,
				consultaId.longValue());
		assertThat(guardada).doesNotContain("ana@correo.com").contains("[dato omitido]");
	}

	@Test
	void despuesDeDiezPreguntasEnElDiaResponde429ConCodigo() throws Exception {
		subirManual(modelo).andExpect(status().isOk());
		for (int i = 0; i < 10; i++) {
			preguntar(vehiculoId, "¿Qué presión llevan las llantas?").andExpect(status().isOk());
		}

		preguntar(vehiculoId, "¿Qué presión llevan las llantas?").andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.codigo").value("ASISTENTE_LIMITE"))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void soloElClienteDeLaAppPuedePreguntar() throws Exception {
		mockMvc.perform(post("/api/v1/app/asistente/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":" + vehiculoId + ",\"pregunta\":\"hola\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/app/asistente/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehiculoId\":" + vehiculoId + ",\"pregunta\":\" \"}")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isBadRequest());
	}

	@Test
	void calificarUnaRespuestaGuardaSiFueUtilYSePuedeCambiar() throws Exception {
		subirManual(modelo).andExpect(status().isOk());
		long consultaId = consultaDe(preguntar(vehiculoId, "¿Cada cuánto cambio el aceite?"));

		calificar(consultaId, token, "{\"util\":true}").andExpect(status().isNoContent());
		assertThat(utilDe(consultaId)).isTrue();

		calificar(consultaId, token, "{\"util\":false}").andExpect(status().isNoContent());
		assertThat(utilDe(consultaId)).isFalse();
	}

	@Test
	void calificarUnaConsultaAjenaOInexistenteResponde404YSinUtilResponde400() throws Exception {
		subirManual(modelo).andExpect(status().isOk());
		long consultaId = consultaDe(preguntar(vehiculoId, "¿Cada cuánto cambio el aceite?"));

		String unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		Long otro = registroClientesService
				.registrar(new NuevoCliente("Otro Cliente", "otro-" + unico + "@correo.com", null, null)).id();
		cuentasAppService.abrir(otro, "Temporal123");
		String tokenAjeno = jwtService.generateToken(otro, "otro-" + unico + "@correo.com",
				List.of(Role.CLIENTE_CARECAR));

		calificar(consultaId, tokenAjeno, "{\"util\":true}").andExpect(status().isNotFound());
		calificar(Long.MAX_VALUE, token, "{\"util\":true}").andExpect(status().isNotFound());
		calificar(consultaId, token, "{}").andExpect(status().isBadRequest());
		assertThat(utilDe(consultaId)).isNull();
	}

	private ResultActions calificar(long consultaId, String conToken, String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/v1/app/asistente/consultas/" + consultaId + "/calificacion")
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + conToken));
	}

	private static long consultaDe(ResultActions respuesta) throws Exception {
		String cuerpo = respuesta.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.consultaId")).longValue();
	}

	private Boolean utilDe(long consultaId) {
		return jdbc.queryForObject("select util from consulta_asistente where id = ?", Boolean.class, consultaId);
	}

	private ResultActions preguntar(Long vehiculo, String pregunta) throws Exception {
		String cuerpo = "{\"vehiculoId\":" + vehiculo + ",\"pregunta\":\"" + pregunta.replace("\"", "\\\"") + "\"}";
		return mockMvc.perform(post("/api/v1/app/asistente/preguntas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions subirManual(String modeloDelManual) throws Exception {
		return mockMvc.perform(multipart("/api/v1/manuales")
				.file(new MockMultipartFile("archivo", "manual.pdf", "application/pdf", manualDeTresPaginas()))
				.param("marca", "Nissan").param("modelo", modeloDelManual).param("anio", "2026")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDelGerente()));
	}

	private static byte[] manualDeTresPaginas() {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document();
		PdfWriter.getInstance(documento, salida);
		documento.open();
		documento.add(new Paragraph("Manual de propietario. Índice general."));
		documento.newPage();
		documento.add(new Paragraph(
				"Mantenimiento. Cambio de aceite del motor: cada 10 000 km o un año, " + "lo que ocurra primero."));
		documento.newPage();
		documento.add(new Paragraph("Llantas. La presión de las llantas en frío es de 32 psi."));
		documento.close();
		return salida.toByteArray();
	}

	private String tokenDelGerente() {
		Rol rol = rolRepository.findByNombre("GERENTE_MARKETING").orElseThrow();
		Usuario usuario = usuarioRepository
				.save(new Usuario("gerente-" + UUID.randomUUID().toString().substring(0, 8), "hash", "Gerente", rol));
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(rol.comoRole()));
	}
}
