package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
 * Agenda de citas de la app (APP-3.3) de punta a punta con el horario por
 * omisión (lunes a sábado de 9:00 a 17:00, franjas de una hora y cupo de 3),
 * migración V21 y base de datos real (Testcontainers). Cada prueba se revierte
 * al terminar.
 */
@AutoConfigureMockMvc
@Transactional
class AgendaAppIntegrationTest extends AbstractIntegrationTest {

	private static final int CUPO = 3;
	private static final String CITA = """
			{"vehiculoId": %d, "franjaId": %d, "fecha": "%s"}""";

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
	private final List<Long> vehiculosDeAna = new ArrayList<>();
	private Long vehiculoDeBruno;
	private LocalDate diaHabil;

	@BeforeEach
	void registrarClientes() {
		Long ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		for (int i = 1; i <= CUPO + 1; i++) {
			registroClientesService
					.asignarVehiculo(ana,
							new VehiculoNuevo(null, "3N1CK3CD5RL00002" + i, "Nissan", "March", 2026, null))
					.vehiculos().stream().map(vehiculo -> vehiculo.id()).filter(id -> !vehiculosDeAna.contains(id))
					.forEach(vehiculosDeAna::add);
		}
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));

		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		vehiculoDeBruno = registroClientesService
				.asignarVehiculo(bruno, new VehiculoNuevo(null, "3N1CK3CD5RL000031", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));

		diaHabil = LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(3);
		while (diaHabil.getDayOfWeek() == DayOfWeek.SUNDAY) {
			diaHabil = diaHabil.plusDays(1);
		}
	}

	@Test
	void laDisponibilidadMuestraLasFranjasDelDiaYDescuentaLasCitas() throws Exception {
		disponibilidad(diaHabil, diaHabil).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8))
				.andExpect(jsonPath("$[0].fecha").value(diaHabil.toString()))
				.andExpect(jsonPath("$[0].franjaId").value(1)).andExpect(jsonPath("$[0].horaInicio").value("09:00"))
				.andExpect(jsonPath("$[0].horaFin").value("10:00")).andExpect(jsonPath("$[0].cupoDisponible").value(3))
				.andExpect(jsonPath("$[7].horaInicio").value("16:00"));

		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 1, diaHabil).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.vehiculoId").value(vehiculosDeAna.getFirst()))
				.andExpect(jsonPath("$.franjaId").value(1)).andExpect(jsonPath("$.vehiculo").value("Nissan March 2026"))
				.andExpect(jsonPath("$.horaInicio").value("09:00")).andExpect(jsonPath("$.horaFin").value("10:00"));

		disponibilidad(diaHabil, diaHabil).andExpect(jsonPath("$[0].cupoDisponible").value(2));
	}

	@Test
	void unaFranjaLlenaResponde409ConFranjaSinCupoYDesapareceDeLaDisponibilidad() throws Exception {
		for (int i = 0; i < CUPO; i++) {
			agendar(tokenDeAna, vehiculosDeAna.get(i), 2, diaHabil).andExpect(status().isCreated());
		}
		agendar(tokenDeBruno, vehiculoDeBruno, 2, diaHabil).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("FRANJA_SIN_CUPO"))
				.andExpect(jsonPath("$.path").value("/api/v1/app/citas"));
		disponibilidad(diaHabil, diaHabil).andExpect(jsonPath("$.length()").value(7))
				.andExpect(jsonPath("$[1].franjaId").value(3));
	}

	@Test
	void elMismoVehiculoNoReservaDosVecesLaMismaFranja() throws Exception {
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 3, diaHabil).andExpect(status().isCreated());
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 3, diaHabil).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_DUPLICADA"));
	}

	@Test
	void elMismoVehiculoNoTieneDosCitasElMismoDia() throws Exception {
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 3, diaHabil).andExpect(status().isCreated());
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 5, diaHabil).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_DUPLICADA"))
				.andExpect(jsonPath("$.path").value("/api/v1/app/citas"));
		agendar(tokenDeAna, vehiculosDeAna.get(1), 5, diaHabil).andExpect(status().isCreated());
	}

	@Test
	void guardaYDevuelveElMotivoOpcional() throws Exception {
		String conMotivo = """
				{"vehiculoId": %d, "franjaId": 1, "fecha": "%s", "motivo": "Garantia"}"""
				.formatted(vehiculosDeAna.getFirst(), diaHabil);
		mockMvc.perform(post("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
				.contentType(MediaType.APPLICATION_JSON).content(conMotivo)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.motivo").value("garantia"));
		agendar(tokenDeAna, vehiculosDeAna.get(1), 1, diaHabil).andExpect(status().isCreated())
				.andExpect(jsonPath("$.motivo").doesNotExist());

		mockMvc.perform(get("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(jsonPath("$.contenido[0].motivo").value("garantia"));

		String invalido = """
				{"vehiculoId": %d, "franjaId": 2, "fecha": "%s", "motivo": "paseo"}"""
				.formatted(vehiculosDeAna.getFirst(), diaHabil);
		mockMvc.perform(post("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
				.contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void reprogramaLiberandoLaFranjaAnteriorYOcupandoLaNueva() throws Exception {
		for (int i = 0; i < CUPO; i++) {
			agendar(tokenDeAna, vehiculosDeAna.get(i), 2, diaHabil).andExpect(status().isCreated());
		}
		long citaId = JsonPath.<Number>read(
				agendar(tokenDeBruno, vehiculoDeBruno, 4, diaHabil).andReturn().getResponse().getContentAsString(),
				"$.id").longValue();

		reprogramar(tokenDeBruno, citaId, "{\"franjaId\": 2}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("FRANJA_SIN_CUPO"));

		reprogramar(tokenDeBruno, citaId, "{\"franjaId\": 6}").andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(citaId)).andExpect(jsonPath("$.franjaId").value(6))
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"));
		disponibilidad(diaHabil, diaHabil).andExpect(jsonPath("$[?(@.franjaId == 4)].cupoDisponible").value(3))
				.andExpect(jsonPath("$[?(@.franjaId == 6)].cupoDisponible").value(2));

		reprogramar(tokenDeAna, citaId, "{\"franjaId\": 7}").andExpect(status().isNotFound());
		reprogramar(tokenDeBruno, citaId, "{\"franjaId\": 6}").andExpect(status().isBadRequest());
		reprogramar(tokenDeBruno, citaId, "{}").andExpect(status().isBadRequest());
	}

	@Test
	void reprogramaAOtroDiaConFechaYNoReprogramaLasCanceladas() throws Exception {
		long citaId = JsonPath.<Number>read(agendar(tokenDeAna, vehiculosDeAna.getFirst(), 1, diaHabil).andReturn()
				.getResponse().getContentAsString(), "$.id").longValue();
		LocalDate otroDia = diaHabil.plusDays(1);
		if (otroDia.getDayOfWeek() == DayOfWeek.SUNDAY) {
			otroDia = otroDia.plusDays(1);
		}
		reprogramar(tokenDeAna, citaId, "{\"franjaId\": 3, \"fecha\": \"%s\"}".formatted(otroDia))
				.andExpect(status().isOk()).andExpect(jsonPath("$.fecha").value(otroDia.toString()))
				.andExpect(jsonPath("$.franjaId").value(3));

		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 2, diaHabil).andExpect(status().isCreated());
		reprogramar(tokenDeAna, citaId, "{\"franjaId\": 8, \"fecha\": \"%s\"}".formatted(diaHabil))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CITA_DUPLICADA"));

		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk());
		reprogramar(tokenDeAna, citaId, "{\"franjaId\": 4}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_NO_REPROGRAMABLE"));
	}

	@Test
	void elComprobanteDaElCodigoDelQrYNoSirveCanceladaNiAjena() throws Exception {
		long citaId = JsonPath.<Number>read(agendar(tokenDeAna, vehiculosDeAna.getFirst(), 1, diaHabil).andReturn()
				.getResponse().getContentAsString(), "$.id").longValue();
		mockMvc.perform(get("/api/v1/app/citas/{id}/comprobante", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk()).andExpect(jsonPath("$.citaId").value(citaId))
				.andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.startsWith("CARECAR-CITA:" + citaId + ".")))
				.andExpect(jsonPath("$.fecha").value(diaHabil.toString()))
				.andExpect(jsonPath("$.horaInicio").value("09:00")).andExpect(jsonPath("$.horaFin").value("10:00"))
				.andExpect(jsonPath("$.vehiculoNombre").value("Nissan March 2026"));
		mockMvc.perform(get("/api/v1/app/citas/{id}/comprobante", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeBruno)).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/app/citas/{id}/comprobante", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_CANCELADA"));
	}

	@Test
	void elTicketLlevaElQrFirmadoYSoloLoVeSuDueno() throws Exception {
		long citaId = JsonPath.<Number>read(agendar(tokenDeAna, vehiculosDeAna.getFirst(), 1, diaHabil).andReturn()
				.getResponse().getContentAsString(), "$.id").longValue();
		mockMvc.perform(
				get("/api/v1/app/citas/{id}/ticket", citaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/app/citas/{id}/ticket", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeBruno)).andExpect(status().isNotFound());
	}

	@Test
	void rechazaDatosInvalidosYVehiculosAjenos() throws Exception {
		agendar(tokenDeAna, vehiculoDeBruno, 1, diaHabil).andExpect(status().isNotFound());
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 99, diaHabil).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 1, diaHabil.minusDays(10)).andExpect(status().isBadRequest());
		LocalDate domingo = diaHabil.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SUNDAY));
		agendar(tokenDeAna, vehiculosDeAna.getFirst(), 1, domingo).andExpect(status().isBadRequest());
		disponibilidad(diaHabil, diaHabil.minusDays(1)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/app/agenda/disponibilidad").param("desde", diaHabil.toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)).andExpect(status().isBadRequest());
	}

	@Test
	void elClienteListaSoloSusCitasYLasCancelaUnaVez() throws Exception {
		String cuerpo = agendar(tokenDeAna, vehiculosDeAna.getFirst(), 5, diaHabil).andReturn().getResponse()
				.getContentAsString();
		Integer citaId = JsonPath.read(cuerpo, "$.id");
		agendar(tokenDeAna, vehiculosDeAna.get(1), 4, diaHabil).andExpect(status().isCreated());
		agendar(tokenDeBruno, vehiculoDeBruno, 4, diaHabil).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(2))
				.andExpect(jsonPath("$.contenido[0].franjaId").value(4))
				.andExpect(jsonPath("$.contenido[0].vehiculo").value("Nissan March 2026"))
				.andExpect(jsonPath("$.contenido[1].id").value(citaId))
				.andExpect(jsonPath("$.totalElementos").value(2));

		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeBruno)).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
		mockMvc.perform(post("/api/v1/app/citas/{id}/cancelar", citaId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeAna)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_NO_CANCELABLE"));

		mockMvc.perform(get("/api/v1/app/citas").param("estado", "CONFIRMADA").param("desde", diaHabil.toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].franjaId").value(4));
		mockMvc.perform(get("/api/v1/app/citas").param("hasta", diaHabil.minusDays(1).toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna))
				.andExpect(jsonPath("$.contenido.length()").value(0));
	}

	@Test
	void soloElClienteUsaLaAgenda() throws Exception {
		mockMvc.perform(get("/api/v1/app/citas")).andExpect(status().isUnauthorized());
	}

	private ResultActions disponibilidad(LocalDate desde, LocalDate hasta) throws Exception {
		return mockMvc.perform(get("/api/v1/app/agenda/disponibilidad").param("desde", desde.toString())
				.param("hasta", hasta.toString()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna));
	}

	private ResultActions reprogramar(String token, long citaId, String cuerpo) throws Exception {
		return mockMvc.perform(
				post("/api/v1/app/citas/{id}/reprogramar", citaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions agendar(String token, Long vehiculoId, long franjaId, LocalDate fecha) throws Exception {
		return mockMvc.perform(post("/api/v1/app/citas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(CITA.formatted(vehiculoId, franjaId, fecha)));
	}
}
