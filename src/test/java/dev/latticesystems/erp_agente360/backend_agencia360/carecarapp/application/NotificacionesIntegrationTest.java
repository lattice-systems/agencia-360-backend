package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush.Notificacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Plataforma;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DispositivoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones push (APP-6) con base real y el envío simulado: registro de
 * tokens, recordatorios de cita (el aviso de servicio terminado corre en su
 * propia transaccion y se prueba aparte).
 */
@AutoConfigureMockMvc
@Transactional
class NotificacionesIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private DispositivoAppRepository dispositivos;

	@Autowired
	private CitaRepository citaRepository;

	@Autowired
	private NotificacionesService notificaciones;

	@MockitoBean
	private EnvioPush envioPush;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private Long ana;
	private Long vehiculoDeAna;
	private String tokenDeAna;
	private String tokenDeBruno;

	@BeforeEach
	void registrarClientes() {
		ana = registroClientesService.registrar(new NuevoCliente("Ana López", "ana@correo.com", null, null)).id();
		vehiculoDeAna = registroClientesService
				.asignarVehiculo(ana, new VehiculoNuevo(null, "3N1CK3CD5RL000091", "Nissan", "March", 2026, null))
				.vehiculos().getFirst().id();
		cuentasAppService.abrir(ana, "Temporal123");
		tokenDeAna = jwtService.generateToken(ana, "ana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Long bruno = registroClientesService.registrar(new NuevoCliente("Bruno Díaz", "bruno@correo.com", null, null))
				.id();
		cuentasAppService.abrir(bruno, "Temporal123");
		tokenDeBruno = jwtService.generateToken(bruno, "bruno@correo.com", List.of(Role.CLIENTE_CARECAR));
		when(envioPush.enviar(anyList(), any())).thenReturn(Set.of());
	}

	@Test
	void elTokenSeRegistraUnaVezPasaAlUltimoClienteYSeBorra() throws Exception {
		registrar(tokenDeAna, "fcm-1").andExpect(status().isNoContent());
		registrar(tokenDeAna, "fcm-1").andExpect(status().isNoContent());
		assertThat(dispositivos.findByClienteId(ana)).hasSize(1);

		registrar(tokenDeBruno, "fcm-1").andExpect(status().isNoContent());
		assertThat(dispositivos.findByClienteId(ana)).isEmpty();

		mockMvc.perform(delete("/api/v1/app/dispositivos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-1\"}"))
				.andExpect(status().isNoContent());
		assertThat(dispositivos.findByToken("fcm-1")).isPresent();
		mockMvc.perform(delete("/api/v1/app/dispositivos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeBruno)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-1\"}"))
				.andExpect(status().isNoContent());
		assertThat(dispositivos.findByToken("fcm-1")).isEmpty();

		mockMvc.perform(put("/api/v1/app/dispositivos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAna)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\",\"plataforma\":\"WINDOWS\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void losRecordatoriosSeMandanUnaSolaVezYLosTokensVencidosSeBorran() {
		notificaciones.registrar(ana, "fcm-ana", Plataforma.ANDROID);
		LocalDate dia = LocalDate.of(2030, 3, 5);
		Cita cita = citaRepository
				.save(Cita.confirmar(ana, vehiculoDeAna, dia, 2L, LocalTime.of(10, 0), LocalTime.of(11, 0)));

		assertThat(recordatoriosA(dia.minusDays(2).atTime(9, 0)).enviarPendientes()).isZero();
		assertThat(recordatoriosA(dia.minusDays(1).atTime(11, 0)).enviarPendientes()).isEqualTo(1);
		assertThat(recordatoriosA(dia.minusDays(1).atTime(12, 0)).enviarPendientes()).isZero();
		assertThat(cita.getRecordatorio24hEn()).isNotNull();

		when(envioPush.enviar(anyList(), any())).thenReturn(Set.of("fcm-ana"));
		assertThat(recordatoriosA(dia.atTime(8, 30)).enviarPendientes()).isEqualTo(1);
		assertThat(recordatoriosA(dia.atTime(9, 0)).enviarPendientes()).isZero();
		assertThat(dispositivos.findByClienteId(ana)).isEmpty();

		ArgumentCaptor<Notificacion> enviada = ArgumentCaptor.forClass(Notificacion.class);
		verify(envioPush, org.mockito.Mockito.times(2)).enviar(anyList(), enviada.capture());
		assertThat(enviada.getAllValues().getLast().datos()).containsEntry("tipo", "CITA").containsEntry("citaId",
				String.valueOf(cita.getId()));
	}

	@Test
	void sinTelefonosNoSeIntentaElEnvio() {
		notificaciones.avisar(ana, new Notificacion("Hola", "Prueba", java.util.Map.of()));
		verify(envioPush, never()).enviar(anyList(), any());
	}

	private RecordatoriosService recordatoriosA(LocalDateTime ahora) {
		return new RecordatoriosService(citaRepository, notificaciones,
				Clock.fixed(ahora.atZone(AgendaAppService.ZONA_AGENCIA).toInstant(), AgendaAppService.ZONA_AGENCIA));
	}

	private ResultActions registrar(String token, String fcm) throws Exception {
		return mockMvc.perform(put("/api/v1/app/dispositivos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"plataforma\":\"ANDROID\"}".formatted(fcm)));
	}
}
