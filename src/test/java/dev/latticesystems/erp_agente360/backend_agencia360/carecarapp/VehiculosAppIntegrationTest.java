package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vehículos y manuales de la app (APP-3.1) de punta a punta: migración V19,
 * seguridad, carga del PDF, URL temporal firmada y base de datos real
 * (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class VehiculosAppIntegrationTest extends AbstractIntegrationTest {

	private static final byte[] PDF = "%PDF-1.4\nmanual de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);

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

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private int usuariosCreados;
	private String tokenDeAna;
	private Long sentraId;
	private Long vehiculoDeBrunoId;

	@BeforeEach
	void registrarClientesConSusVehiculos() {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		registroClientesService.asignarVehiculo(ana,
				new VehiculoNuevo(null, "3N1CK3CD5RL000001", "Nissan", "March", 2026, "Sense"));
		sentraId = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1AB8CV5RY000002", "Nissan", "Sentra", 2025, "Advance"))
				.vehiculos().stream().filter(vehiculo -> vehiculo.modelo().equals("Sentra")).findFirst().orElseThrow()
				.id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));

		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		vehiculoDeBrunoId = registroClientesService
				.asignarVehiculo(bruno, new VehiculoNuevo(null, "3N1CK3CD5RL000003", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
	}

	@Test
	void elClienteVeSusVehiculosConMarcaYVersion() throws Exception {
		mockMvc.perform(get("/api/v1/app/vehiculos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].vin").value("3N1CK3CD5RL000001"))
				.andExpect(jsonPath("$[0].marca").value("Nissan")).andExpect(jsonPath("$[0].modelo").value("March"))
				.andExpect(jsonPath("$[0].anio").value(2026)).andExpect(jsonPath("$[0].version").value("Sense"))
				.andExpect(jsonPath("$[1].modelo").value("Sentra"));
	}

	@Test
	void elClienteDescargaElManualDeSuVehiculoConLaUrlTemporal() throws Exception {
		subirManual("nissan", "MARCH", 2026, PDF).andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("nissan MARCH 2026 Manual de propietario"));
		Long vehiculoMarch = idDelVehiculo("3N1CK3CD5RL000001");

		String respuesta = mockMvc
				.perform(get("/api/v1/app/vehiculos/{id}/manuales", vehiculoMarch).header(HttpHeaders.AUTHORIZATION,
						"Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].anio").value(2026))
				.andExpect(jsonPath("$[0].titulo").value("nissan MARCH 2026 Manual de propietario")).andReturn()
				.getResponse().getContentAsString();
		String url = JsonPath.read(respuesta, "$[0].url");
		assertThat(url).contains("/api/v1/app/manuales/").contains("/archivo?expira=").contains("&firma=");

		mockMvc.perform(get(url.substring(url.indexOf("/api/v1/")))).andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(content().bytes(PDF));
	}

	@Test
	void unaUrlAlteradaOSinFirmaNoDescargaElManual() throws Exception {
		Integer manualId = JsonPath
				.read(subirManual("Nissan", "March", 2026, PDF).andReturn().getResponse().getContentAsString(), "$.id");
		long enUnaHora = System.currentTimeMillis() / 1000 + 3600;

		mockMvc.perform(get("/api/v1/app/manuales/{id}/archivo", manualId).param("expira", String.valueOf(enUnaHora))
				.param("firma", "firma-inventada")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/manuales/{id}/archivo", manualId)).andExpect(status().isNotFound());
	}

	@Test
	void unVehiculoSinManualRegresaUnaListaVaciaYUnoAjenoResponde404() throws Exception {
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/manuales", sentraId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/manuales", vehiculoDeBrunoId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isNotFound());
	}

	@Test
	void subirOtraVezElManualLoReemplaza() throws Exception {
		Integer primero = JsonPath
				.read(subirManual("Nissan", "March", 2026, PDF).andReturn().getResponse().getContentAsString(), "$.id");
		byte[] corregido = "%PDF-1.7\nmanual corregido".getBytes(StandardCharsets.US_ASCII);

		subirManual("NISSAN", "march", 2026, corregido).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(primero))
				.andExpect(jsonPath("$.tamanoBytes").value(corregido.length));
	}

	@Test
	void soloAceptaPdfs() throws Exception {
		subirManual("Nissan", "March", 2026, "no soy un pdf".getBytes(StandardCharsets.UTF_8))
				.andExpect(status().isBadRequest());
		subirManual("Nissan", " ", 2026, PDF).andExpect(status().isBadRequest());
		subirManual("Nissan", "March", 1800, PDF).andExpect(status().isBadRequest());
	}

	@Test
	void soloElGerenteDeMarketingSubeManualesYSoloElClienteVeSusVehiculos() throws Exception {
		mockMvc.perform(
				multipart("/api/v1/manuales").file(new MockMultipartFile("archivo", "m.pdf", "application/pdf", PDF))
						.param("marca", "Nissan").param("modelo", "March").param("anio", "2026")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDePersonal("VENTAS")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/app/vehiculos").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDePersonal("GERENTE_MARKETING"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/app/vehiculos")).andExpect(status().isUnauthorized());
	}

	private ResultActions subirManual(String marca, String modelo, int anio, byte[] contenido) throws Exception {
		return mockMvc.perform(multipart("/api/v1/manuales")
				.file(new MockMultipartFile("archivo", "manual.pdf", "application/pdf", contenido))
				.param("marca", marca).param("modelo", modelo).param("anio", String.valueOf(anio))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDePersonal("GERENTE_MARKETING")));
	}

	private Long idDelVehiculo(String vin) throws Exception {
		String respuesta = mockMvc
				.perform(get("/api/v1/app/vehiculos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andReturn().getResponse().getContentAsString();
		List<Integer> ids = JsonPath.read(respuesta, "$[?(@.vin == '" + vin + "')].id");
		return ids.getFirst().longValue();
	}

	private String tokenDePersonal(String rol) {
		Rol encontrado = rolRepository.findByNombre(rol).orElseThrow();
		usuariosCreados++;
		Usuario usuario = usuarioRepository
				.save(new Usuario("manuales" + usuariosCreados, "hash-de-prueba", "Persona " + rol, encontrado));
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(encontrado.comoRole()));
	}
}
