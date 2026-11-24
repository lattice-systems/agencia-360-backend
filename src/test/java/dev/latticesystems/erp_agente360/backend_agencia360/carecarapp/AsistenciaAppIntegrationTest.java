package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de asistencia (V2-M11.01) de punta a punta con migración V26 y
 * base de datos real (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class AsistenciaAppIntegrationTest extends AbstractIntegrationTest {

	private static final String RUTA = "/api/v1/app/asistencia";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeAna;
	private String tokenDeBruno;
	private Long vehiculoDeAna;
	private Long vehiculoDeBruno;

	@BeforeEach
	void registrarClientes() {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		vehiculoDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000041", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));

		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		vehiculoDeBruno = registroClientesService
				.asignarVehiculo(bruno, new VehiculoNuevo(null, "3N1CK3CD5RL000042", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
	}

	@Test
	void creaLaSolicitudRecibidaConUbicacionYLaDevuelveComoActiva() throws Exception {
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "sin_gasolina", "20.5", "-100.25", "Sin gas en la carretera"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("recibida"))
				.andExpect(jsonPath("$.tipo").value("sin_gasolina"))
				.andExpect(jsonPath("$.vehiculoId").value(vehiculoDeAna)).andExpect(jsonPath("$.lat").value(20.5))
				.andExpect(jsonPath("$.lng").value(-100.25))
				.andExpect(jsonPath("$.comentario").value("Sin gas en la carretera"))
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.creadaEn").exists());

		activa(tokenDeAna).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("recibida"))
				.andExpect(jsonPath("$.tipo").value("sin_gasolina"));
	}

	@Test
	void laUbicacionYElComentarioSonOpcionales() throws Exception {
		pedir(tokenDeAna, "{\"vehiculoId\": %d, \"tipo\": \"LLANTA\"}".formatted(vehiculoDeAna))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("llanta"))
				.andExpect(jsonPath("$.lat").doesNotExist()).andExpect(jsonPath("$.comentario").doesNotExist());
	}

	@Test
	void sinSolicitudAbiertaLaActivaResponde204() throws Exception {
		activa(tokenDeAna).andExpect(status().isNoContent());
	}

	@Test
	void unVehiculoAjenoOInexistenteResponde404() throws Exception {
		pedir(tokenDeAna, cuerpo(vehiculoDeBruno, "choque", null, null, null)).andExpect(status().isNotFound());
		pedir(tokenDeAna, cuerpo(999_999L, "choque", null, null, null)).andExpect(status().isNotFound());
		activa(tokenDeAna).andExpect(status().isNoContent());
	}

	@Test
	void unaSegundaSolicitudAbiertaResponde409ConSolicitudAbierta() throws Exception {
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "bateria", null, null, null)).andExpect(status().isCreated());
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "llanta", null, null, null)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("SOLICITUD_ABIERTA")).andExpect(jsonPath("$.path").value(RUTA));
		pedir(tokenDeBruno, cuerpo(vehiculoDeBruno, "llanta", null, null, null)).andExpect(status().isCreated());
		activa(tokenDeAna).andExpect(jsonPath("$.tipo").value("bateria"));
	}

	@Test
	void rechazaUbicacionFueraDeRangoIncompletaOTipoDesconocido() throws Exception {
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "llanta", "91", "-100", null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "llanta", "20", "-181", null)).andExpect(status().isBadRequest());
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "llanta", "20", null, null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "grua", null, null, null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		pedir(tokenDeAna, cuerpo(vehiculoDeAna, "llanta", null, null, "x".repeat(501)))
				.andExpect(status().isBadRequest());
		pedir(tokenDeAna, "{\"tipo\": \"llanta\"}").andExpect(status().isBadRequest());
	}

	@Test
	void soloAceptaActivaTrueYExigeSesion() throws Exception {
		mockMvc.perform(get(RUTA).param("activa", "false").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(RUTA).param("activa", "true")).andExpect(status().isUnauthorized());
	}

	private ResultActions pedir(String token, String cuerpo) throws Exception {
		return mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions activa(String token) throws Exception {
		return mockMvc.perform(get(RUTA).param("activa", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static String cuerpo(Long vehiculoId, String tipo, String lat, String lng, String comentario) {
		StringBuilder json = new StringBuilder("{\"vehiculoId\": " + vehiculoId + ", \"tipo\": \"" + tipo + "\"");
		if (lat != null) {
			json.append(", \"lat\": ").append(lat);
		}
		if (lng != null) {
			json.append(", \"lng\": ").append(lng);
		}
		if (comentario != null) {
			json.append(", \"comentario\": \"").append(comentario).append('"');
		}
		return json.append('}').toString();
	}
}
