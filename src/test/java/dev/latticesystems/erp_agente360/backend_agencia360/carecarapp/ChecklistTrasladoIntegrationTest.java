package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.TrasladoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checklist de recolección y entrega (V2-M17.04): carga por el chofer, lectura
 * por el cliente dueño con URL firmadas de vida corta.
 */
@AutoConfigureMockMvc
@Transactional
class ChecklistTrasladoIntegrationTest extends AbstractIntegrationTest {

	private static final String DIRECCION = "{\"alias\": \"Casa\", \"calle\": \"Av. Siempre Viva 742\", "
			+ "\"colonia\": \"Centro\", \"cp\": \"37000\", \"ciudad\": \"León\", \"lat\": 21.13, \"lng\": -101.69}";
	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 4, 5, 6};

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private TrasladoRepository trasladoRepository;

	@Autowired
	private FranjaHorarioRepository franjaHorarioRepository;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private String tokenDeBruno;
	private String tokenDelPersonal;
	private long trasladoId;

	@BeforeEach
	void prepararTraslado() throws Exception {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		Long vehiculo = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000081", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
		tokenDelPersonal = jwtService.generateToken(900_001L, "marketing", List.of(Role.GERENTE_MARKETING));

		LocalDate dia = LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(3);
		while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dia = dia.plusDays(1);
		}
		LocalDate diaDeCita = dia;
		Long franja = franjaHorarioRepository.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream()
				.filter(f -> f.esDe(diaDeCita)).findFirst().orElseThrow().getId();
		String direccion = json(post("/api/v1/app/direcciones"), tokenDeAna, DIRECCION).andReturn().getResponse()
				.getContentAsString();
		long direccionId = JsonPath.<Number>read(direccion, "$.id").longValue();
		String cita = json(post("/api/v1/app/citas"), tokenDeAna,
				"{\"vehiculoId\": %d, \"franjaId\": %d, \"fecha\": \"%s\", \"recoleccion\": {\"direccionId\": %d}}"
						.formatted(vehiculo, franja, dia, direccionId))
				.andReturn().getResponse().getContentAsString();
		long citaId = JsonPath.<Number>read(cita, "$.id").longValue();
		trasladoId = trasladoRepository.findByCitaIdOrderByIdAsc(citaId).getFirst().getId();
	}

	@Test
	void elChoferCargaElChecklistYElClienteLoVeConUrlFirmadas() throws Exception {
		moverA("en_camino");
		moverA("recogido");

		cargar(tokenDelPersonal, "45230", "medio", "Rayón en puerta derecha", 2, true).andExpect(status().isOk())
				.andExpect(jsonPath("$.trasladoId").value(trasladoId)).andExpect(jsonPath("$.km").value(45230))
				.andExpect(jsonPath("$.gasolina").value("medio"))
				.andExpect(jsonPath("$.notas").value("Rayón en puerta derecha"))
				.andExpect(jsonPath("$.fotos.length()").value(2)).andExpect(jsonPath("$.firmaUrl").isString());

		String json = consultar(tokenDeAna).andExpect(status().isOk()).andExpect(jsonPath("$.km").value(45230))
				.andExpect(jsonPath("$.gasolina").value("medio")).andExpect(jsonPath("$.registradoEn").exists())
				.andReturn().getResponse().getContentAsString();
		String foto = JsonPath.read(json, "$.fotos[0]");
		String firma = JsonPath.read(json, "$.firmaUrl");
		assertThat(foto).startsWith("/api/v1/archivos/checklist-fotos/").contains("expira=").contains("firma=");
		assertThat(firma).startsWith("/api/v1/archivos/checklist-firmas/").contains("firma=");
		mockMvc.perform(get(foto)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(content().bytes(JPEG));
		mockMvc.perform(get(firma)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
		mockMvc.perform(get(foto.replaceAll("firma=[^&]+", "firma=alterada"))).andExpect(status().isNotFound());
	}

	@Test
	void volverACargarReemplazaElChecklistYBorraSusArchivos() throws Exception {
		moverA("en_camino");
		cargar(tokenDelPersonal, "100", "lleno", null, 1, false).andExpect(status().isOk())
				.andExpect(jsonPath("$.firmaUrl").doesNotExist());
		String anterior = JsonPath.read(consultar(tokenDeAna).andReturn().getResponse().getContentAsString(),
				"$.fotos[0]");

		cargar(tokenDelPersonal, "150", "medio", "Segunda captura", 0, false).andExpect(status().isOk());

		consultar(tokenDeAna).andExpect(jsonPath("$.km").value(150)).andExpect(jsonPath("$.fotos.length()").value(0))
				.andExpect(jsonPath("$.notas").value("Segunda captura"));
		mockMvc.perform(get(anterior)).andExpect(status().isNotFound());
	}

	@Test
	void sinChecklistOParaUnAjenoResponde404() throws Exception {
		consultar(tokenDeAna).andExpect(status().isNotFound());
		moverA("en_camino");
		cargar(tokenDelPersonal, "100", "lleno", null, 0, false).andExpect(status().isOk());
		consultar(tokenDeBruno).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/traslados/" + trasladoId + "/checklist")).andExpect(status().isUnauthorized());
	}

	@Test
	void unTrasladoProgramadoNoAceptaChecklist() throws Exception {
		cargar(tokenDelPersonal, "100", "lleno", null, 0, false).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		cargar(tokenDelPersonal, "100", "lleno", null, 0, false, 999_999L).andExpect(status().isNotFound());
	}

	@Test
	void soloElPersonalCargaElChecklist() throws Exception {
		moverA("en_camino");
		cargar(tokenDeAna, "100", "lleno", null, 0, false).andExpect(status().isForbidden());
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/traslados/" + trasladoId + "/checklist").param("km", "100")
				.param("gasolina", "lleno")).andExpect(status().isUnauthorized());
	}

	@Test
	void rechazaDatosInvalidosSinGuardarNada() throws Exception {
		moverA("en_camino");

		cargar(tokenDelPersonal, "0", "lleno", null, 0, false).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		cargar(tokenDelPersonal, "2000001", "lleno", null, 0, false).andExpect(status().isBadRequest());
		cargar(tokenDelPersonal, "100", "rebosando", null, 0, false).andExpect(status().isBadRequest());
		cargar(tokenDelPersonal, "100", "lleno", "x".repeat(501), 0, false).andExpect(status().isBadRequest());
		cargar(tokenDelPersonal, "100", "lleno", null, 11, false).andExpect(status().isBadRequest());
		MockMultipartHttpServletRequestBuilder conPdf = multipart(HttpMethod.PUT,
				"/api/v1/traslados/" + trasladoId + "/checklist")
				.file(new MockMultipartFile("fotos", "a.pdf", "application/pdf", new byte[]{1, 2})).param("km", "100")
				.param("gasolina", "lleno");
		mockMvc.perform(conPdf.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDelPersonal))
				.andExpect(status().isBadRequest());
		consultar(tokenDeAna).andExpect(status().isNotFound());
	}

	private void moverA(String estado) throws Exception {
		json(patch("/api/v1/traslados/" + trasladoId + "/estado"), tokenDelPersonal, "{\"estado\": \"" + estado + "\"}")
				.andExpect(status().isOk());
	}

	private ResultActions consultar(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/app/traslados/" + trasladoId + "/checklist")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions cargar(String token, String km, String gasolina, String notas, int fotos, boolean firma)
			throws Exception {
		return cargar(token, km, gasolina, notas, fotos, firma, trasladoId);
	}

	private ResultActions cargar(String token, String km, String gasolina, String notas, int fotos, boolean firma,
			long id) throws Exception {
		MockMultipartHttpServletRequestBuilder peticion = multipart(HttpMethod.PUT,
				"/api/v1/traslados/" + id + "/checklist").param("km", km).param("gasolina", gasolina);
		if (notas != null) {
			peticion.param("notas", notas);
		}
		for (int i = 0; i < fotos; i++) {
			peticion.file(new MockMultipartFile("fotos", "foto" + i + ".jpg", "image/jpeg", JPEG));
		}
		if (firma) {
			peticion.file(new MockMultipartFile("firma", "firma.png", "image/png", PNG));
		}
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
			String token, String cuerpo) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}
}
