package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RegistroPaseAppleRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio web de pases de Apple (V2-M25.06) de punta a punta con base real:
 * registro de dispositivos, lista de pases cambiados, entrega del pase vigente
 * (anulado al cancelar) y que el resto de la API siga protegido. Wallet real no
 * participa: se llama con las rutas y encabezados que Apple documenta.
 */
@AutoConfigureMockMvc
@Transactional
class PasesAppleIntegrationTest extends AbstractIntegrationTest {

	private static final String DISPOSITIVO = "a1b2c3d4e5f6";
	private static final String PUSH_TOKEN = "00112233445566778899aabbccddeeff";
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Apple configurado con credenciales generadas y una URL pública de mentira.
	 */
	@TestConfiguration
	static class ConfiguracionDeApple {
		@Bean
		@Primary
		ConfiguracionWallet configuracionWalletConServicioWeb() {
			ConfiguracionWallet base = CredencialesWalletDePrueba.nuevas().configuracion();
			return new ConfiguracionWallet(base.applePassTypeId(), base.appleTeamId(), base.appleCertP12Base64(),
					base.appleCertPassword(), base.appleWwdrPemBase64(), "", "", "https://carecar.example.com");
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private FranjaHorarioRepository franjaHorarioRepository;

	@Autowired
	private RegistroPaseAppleRepository registros;

	@Autowired
	private TokenPaseApple tokenPase;

	@Autowired
	private ConfiguracionWallet configuracion;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private long citaId;
	private String serial;
	private String autorizacion;
	private String passType;

	@BeforeEach
	void agendarUnaCita() throws Exception {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@pases.com", null, null)).id();
		Long vehiculo = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000071", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@pases.com", List.of(Role.CLIENTE_CARECAR));
		LocalDate dia = LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(3);
		while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dia = dia.plusDays(1);
		}
		LocalDate fecha = dia;
		Long franja = franjaHorarioRepository.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream()
				.filter(candidata -> candidata.esDe(fecha)).map(FranjaHorario::getId).findFirst().orElseThrow();
		String cuerpoDeCita = "{\"vehiculoId\": %d, \"franjaId\": %d, \"fecha\": \"%s\"}".formatted(vehiculo, franja,
				fecha);
		MvcResult creada = mockMvc
				.perform(post("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
						.contentType(MediaType.APPLICATION_JSON).content(cuerpoDeCita))
				.andExpect(status().isCreated()).andReturn();
		citaId = JsonPath.<Number>read(creada.getResponse().getContentAsString(), "$.id").longValue();
		serial = "cita-" + citaId;
		passType = configuracion.applePassTypeId();
		autorizacion = "ApplePass " + tokenPase.de(citaId);
	}

	@Test
	void registrarExigeElTokenDelPase() throws Exception {
		registrar(null, PUSH_TOKEN).andExpect(status().isUnauthorized());
		registrar("ApplePass inventado", PUSH_TOKEN).andExpect(status().isUnauthorized());
		registrar("Bearer " + tokenDeAna, PUSH_TOKEN).andExpect(status().isUnauthorized());
		assertThat(registros.findByCitaId(citaId)).isEmpty();
	}

	@Test
	void unTokenDeOtraCitaNoRegistra() throws Exception {
		registrar("ApplePass " + tokenPase.de(citaId + 1), PUSH_TOKEN).andExpect(status().isUnauthorized());
	}

	@Test
	void registrarCreaYLaSegundaVezActualizaElPushToken() throws Exception {
		registrar(autorizacion, PUSH_TOKEN).andExpect(status().isCreated());
		registrar(autorizacion, "ffeeddccbbaa99887766554433221100").andExpect(status().isOk());

		assertThat(registros.findByCitaId(citaId)).hasSize(1).allSatisfy(
				registro -> assertThat(registro.getPushToken()).isEqualTo("ffeeddccbbaa99887766554433221100"));
	}

	@Test
	void unPassTypeAjenoOUnSerialRaroNoExisten() throws Exception {
		mockMvc.perform(post("/v1/devices/{d}/registrations/{p}/{s}", DISPOSITIVO, "pass.otro.tipo", serial)
				.header(HttpHeaders.AUTHORIZATION, autorizacion).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoDeRegistro(PUSH_TOKEN))).andExpect(status().isNotFound());
		mockMvc.perform(post("/v1/devices/{d}/registrations/{p}/{s}", DISPOSITIVO, passType, "cita-abc")
				.header(HttpHeaders.AUTHORIZATION, autorizacion).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoDeRegistro(PUSH_TOKEN))).andExpect(status().isNotFound());
	}

	@Test
	void sinPushTokenEsUnaPeticionInvalida() throws Exception {
		registrar(autorizacion, "").andExpect(status().isBadRequest());
	}

	@Test
	void laListaDeCambiosEntregaElSerialYLuegoSoloLoNuevo() throws Exception {
		registrar(autorizacion, PUSH_TOKEN).andExpect(status().isCreated());

		MvcResult primera = listar(null).andExpect(status().isOk())
				.andExpect(jsonPath("$.serialNumbers[0]").value(serial)).andReturn();
		String etiqueta = JsonPath.read(primera.getResponse().getContentAsString(), "$.lastUpdated");
		listar(etiqueta).andExpect(status().isNoContent());

		Thread.sleep(20);
		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk());
		listar(etiqueta).andExpect(status().isOk()).andExpect(jsonPath("$.serialNumbers[0]").value(serial));
	}

	@Test
	void unDispositivoSinPasesRecibeNoContent() throws Exception {
		listar(null).andExpect(status().isNoContent());
	}

	@Test
	void elPaseSeEntregaFirmadoYAnuladoAlCancelarLaCita() throws Exception {
		JsonNode vigente = pasarJson(pase(autorizacion, null).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.pkpass"))
				.andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andReturn());
		assertThat(vigente.path("serialNumber").asText()).isEqualTo(serial);
		assertThat(vigente.path("webServiceURL").asText()).isEqualTo("https://carecar.example.com");
		assertThat(vigente.path("authenticationToken").asText()).isEqualTo(tokenPase.de(citaId));
		assertThat(vigente.has("voided")).isFalse();

		Thread.sleep(1100);
		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk());

		JsonNode anulado = pasarJson(pase(autorizacion, null).andExpect(status().isOk()).andReturn());
		assertThat(anulado.path("voided").asBoolean()).isTrue();
	}

	@Test
	void elPaseNoCambiadoResponde304ConIfModifiedSince() throws Exception {
		String modificado = pase(autorizacion, null).andReturn().getResponse().getHeader(HttpHeaders.LAST_MODIFIED);

		pase(autorizacion, modificado).andExpect(status().isNotModified());
	}

	@Test
	void elPaseExigeElToken() throws Exception {
		pase(null, null).andExpect(status().isUnauthorized());
		pase("ApplePass otro", null).andExpect(status().isUnauthorized());
	}

	@Test
	void darDeBajaBorraElRegistroYEsIdempotente() throws Exception {
		registrar(autorizacion, PUSH_TOKEN).andExpect(status().isCreated());

		baja(autorizacion).andExpect(status().isOk());
		baja(autorizacion).andExpect(status().isOk());
		assertThat(registros.findByCitaId(citaId)).isEmpty();
		baja("ApplePass inventado").andExpect(status().isUnauthorized());
	}

	@Test
	void elLogDeWalletSeAceptaSinSesion() throws Exception {
		mockMvc.perform(post("/v1/log").contentType(MediaType.APPLICATION_JSON)
				.content("{\"logs\": [\"error de prueba\\ncon salto\"]}")).andExpect(status().isOk());
		mockMvc.perform(post("/v1/log").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk());
	}

	@Test
	void soloLasRutasDeAppleEstanAbiertasYElRestoSigueProtegido() throws Exception {
		mockMvc.perform(get("/v1/otra-cosa")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/v1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/app/citas")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/app/citas/{id}/pase", citaId).param("plataforma", "apple"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/clientes")).andExpect(status().isUnauthorized());
	}

	private ResultActions registrar(String autorizacionDelPase, String pushToken) throws Exception {
		var peticion = post("/v1/devices/{d}/registrations/{p}/{s}", DISPOSITIVO, passType, serial)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpoDeRegistro(pushToken));
		if (autorizacionDelPase != null) {
			peticion.header(HttpHeaders.AUTHORIZATION, autorizacionDelPase);
		}
		return mockMvc.perform(peticion);
	}

	private ResultActions baja(String autorizacionDelPase) throws Exception {
		return mockMvc.perform(delete("/v1/devices/{d}/registrations/{p}/{s}", DISPOSITIVO, passType, serial)
				.header(HttpHeaders.AUTHORIZATION, autorizacionDelPase));
	}

	private ResultActions listar(String desde) throws Exception {
		var peticion = get("/v1/devices/{d}/registrations/{p}", DISPOSITIVO, passType);
		if (desde != null) {
			peticion.param("passesUpdatedSince", desde);
		}
		return mockMvc.perform(peticion);
	}

	private ResultActions pase(String autorizacionDelPase, String modificadoDesde) throws Exception {
		var peticion = get("/v1/passes/{p}/{s}", passType, serial);
		if (autorizacionDelPase != null) {
			peticion.header(HttpHeaders.AUTHORIZATION, autorizacionDelPase);
		}
		if (modificadoDesde != null) {
			peticion.header(HttpHeaders.IF_MODIFIED_SINCE, modificadoDesde);
		}
		return mockMvc.perform(peticion);
	}

	private static String cuerpoDeRegistro(String pushToken) {
		return "{\"pushToken\": \"" + pushToken + "\"}";
	}

	private static JsonNode pasarJson(MvcResult resultado) throws Exception {
		Map<String, byte[]> archivos = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(
				new ByteArrayInputStream(resultado.getResponse().getContentAsByteArray()))) {
			for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
				archivos.put(entrada.getName(), zip.readAllBytes());
			}
		}
		assertThat(archivos).containsKeys("pass.json", "manifest.json", "signature");
		return JSON.readTree(archivos.get("pass.json"));
	}
}
