package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado de la solicitud de asistencia (V2-M11.04): consulta del dueño, cambio
 * de estado por el personal, borrado de la ubicación al cerrarse y aviso en
 * vivo al cliente.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class AsistenciaEstadoIntegrationTest extends AbstractIntegrationTest {

	private static final String RUTA = "/api/v1/app/asistencia";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationEvents eventos;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long anaId;
	private String tokenDeAna;
	private String tokenDeBruno;
	private String tokenDelPersonal;
	private Long vehiculoDeAna;

	@BeforeEach
	void registrarClientes() {
		anaId = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		vehiculoDeAna = registroClientesService
				.asignarVehiculo(anaId, new VehiculoNuevo(null, "3N1CK3CD5RL000061", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(anaId, "Temporal123");
		tokenDeAna = jwtService.generateToken(anaId, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));

		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
		tokenDelPersonal = jwtService.generateToken(900_001L, "marketing", List.of(Role.GERENTE_MARKETING));
	}

	@Test
	void elDuenoConsultaSuSolicitudYUnAjenoRecibe404() throws Exception {
		long id = solicitar();

		consultar(tokenDeAna, id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.estado").value("recibida")).andExpect(jsonPath("$.lat").value(20.5));
		consultar(tokenDeBruno, id).andExpect(status().isNotFound());
		consultar(tokenDeAna, 999_999L).andExpect(status().isNotFound());
		mockMvc.perform(get(RUTA + "/" + id)).andExpect(status().isUnauthorized());
	}

	@Test
	void elPersonalLlevaLaSolicitudDeRecibidaAAtendida() throws Exception {
		long id = solicitar();

		cambiar(tokenDelPersonal, id, "en_camino").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("en_camino")).andExpect(jsonPath("$.lat").value(20.5));
		consultar(tokenDeAna, id).andExpect(jsonPath("$.estado").value("en_camino"));
		activa(tokenDeAna).andExpect(status().isOk());

		cambiar(tokenDelPersonal, id, "atendida").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("atendida"));
		activa(tokenDeAna).andExpect(status().isNoContent());
	}

	@Test
	void unaTransicionInvalidaResponde409() throws Exception {
		long id = solicitar();

		cambiar(tokenDelPersonal, id, "atendida").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		cambiar(tokenDelPersonal, id, "recibida").andExpect(status().isConflict());
		cambiar(tokenDelPersonal, id, "cancelada").andExpect(status().isOk());
		cambiar(tokenDelPersonal, id, "en_camino").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		consultar(tokenDeAna, id).andExpect(jsonPath("$.estado").value("cancelada"));
	}

	@Test
	void alCerrarseSeBorraLaUbicacionDeLaSolicitud() throws Exception {
		long id = solicitar();
		cambiar(tokenDelPersonal, id, "en_camino").andExpect(status().isOk());

		cambiar(tokenDelPersonal, id, "atendida").andExpect(status().isOk()).andExpect(jsonPath("$.lat").doesNotExist())
				.andExpect(jsonPath("$.lng").doesNotExist());

		consultar(tokenDeAna, id).andExpect(jsonPath("$.lat").doesNotExist()).andExpect(jsonPath("$.lng").doesNotExist());
		assertThat(jdbcTemplate.queryForObject("select lat from solicitud_asistencia where id = ?", Object.class, id))
				.isNull();
		assertThat(jdbcTemplate.queryForObject("select lng from solicitud_asistencia where id = ?", Object.class, id))
				.isNull();
	}

	@Test
	void cancelarTambienBorraLaUbicacion() throws Exception {
		long id = solicitar();

		cambiar(tokenDelPersonal, id, "cancelada").andExpect(status().isOk()).andExpect(jsonPath("$.lat").doesNotExist());
		assertThat(jdbcTemplate.queryForObject("select lat from solicitud_asistencia where id = ?", Object.class, id))
				.isNull();
	}

	@Test
	void cadaCambioPublicaUnAvisoParaElClienteDuenoYUnaTransicionInvalidaNo() throws Exception {
		long id = solicitar();

		cambiar(tokenDelPersonal, id, "en_camino").andExpect(status().isOk());
		cambiar(tokenDelPersonal, id, "recibida").andExpect(status().isConflict());
		cambiar(tokenDelPersonal, id, "atendida").andExpect(status().isOk());

		List<AvisoAlCliente> avisos = eventos.stream(AvisoAlCliente.class).toList();
		assertThat(avisos).hasSize(2);
		assertThat(avisos).allSatisfy(aviso -> {
			assertThat(aviso.clienteId()).isEqualTo(anaId);
			assertThat(aviso.evento()).isEqualTo("asistencia.estado");
		});
		assertThat(avisos.get(0).datos()).containsEntry("solicitudId", id).containsEntry("estado", "en_camino");
		assertThat(avisos.get(1).datos()).containsEntry("solicitudId", id).containsEntry("estado", "atendida");
	}

	@Test
	void soloElPersonalCambiaElEstado() throws Exception {
		long id = solicitar();

		cambiar(tokenDeAna, id, "en_camino").andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/asistencia/{id}/estado", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\": \"en_camino\"}")).andExpect(status().isUnauthorized());
		cambiar(tokenDelPersonal, 999_999L, "en_camino").andExpect(status().isNotFound());
		cambiar(tokenDelPersonal, id, "grua").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	private long solicitar() throws Exception {
		String json = mockMvc
				.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
						.contentType(MediaType.APPLICATION_JSON).content("{\"vehiculoId\": " + vehiculoDeAna
								+ ", \"tipo\": \"llanta\", \"lat\": 20.5, \"lng\": -100.25}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(json, "$.id").longValue();
	}

	private ResultActions consultar(String token, long id) throws Exception {
		return mockMvc.perform(get(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions activa(String token) throws Exception {
		return mockMvc.perform(get(RUTA).param("activa", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions cambiar(String token, long id, String estado) throws Exception {
		return mockMvc.perform(patch("/api/v1/asistencia/{id}/estado", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"" + estado + "\"}"));
	}
}
