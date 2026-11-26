package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaSeguridad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCampana;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.GravedadCampana;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CampanaSeguridadRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CampanaVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class CampanasAppServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");
	private static final LocalDate DIA = LocalDate.of(2026, 11, 30);
	private static final VehiculoDeCliente MARCH = vehiculo(9L, 3L, "3N1CK3CD5RL000150");

	private final VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
	private final CampanaSeguridadRepository campanas = mock(CampanaSeguridadRepository.class);
	private final CampanaVehiculoRepository delVehiculo = mock(CampanaVehiculoRepository.class);
	private final CitaRepository citas = mock(CitaRepository.class);
	private final AgendaAppService agenda = mock(AgendaAppService.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private final AtomicLong ids = new AtomicLong(100);
	private CampanasAppService servicio;

	@BeforeEach
	void preparar() {
		servicio = new CampanasAppService(vehiculos, campanas, delVehiculo, citas, agenda, eventos,
				Clock.fixed(AHORA, ZoneOffset.UTC));
		when(vehiculos.buscarPorId(9L)).thenReturn(Optional.of(MARCH));
		when(delVehiculo.save(any(CampanaVehiculo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	private static VehiculoDeCliente vehiculo(Long id, Long duenoId, String vin) {
		return new VehiculoDeCliente(id, vin, "Nissan", "March", 2025, null, null, 20_000, null,
				new Propietario(duenoId, "Ana", null, null));
	}

	private CampanaSeguridad campana(String codigo, GravedadCampana gravedad, String vinDesde, String vinHasta) {
		CampanaSeguridad campana = CampanaSeguridad.publicar(new CampanaSeguridad.Datos(codigo, "Título " + codigo,
				"Descripción", "Qué hacer", gravedad, "March", 2024, 2026, vinDesde, vinHasta), AHORA);
		ReflectionTestUtils.setField(campana, "id", ids.incrementAndGet());
		return campana;
	}

	private static CampanaVehiculo delAuto(CampanaSeguridad campana, Long citaAgendada) {
		CampanaVehiculo delAuto = new CampanaVehiculo(campana.getId(), 9L);
		if (citaAgendada != null) {
			delAuto.agendar(citaAgendada);
		}
		return delAuto;
	}

	@Test
	void creaLasQueAplicanYOrdenaAbiertasPorGravedadAntesQueLasAgendadas() {
		CampanaSeguridad media = campana("RC-1", GravedadCampana.MEDIA, null, null);
		CampanaSeguridad critica = campana("RC-2", GravedadCampana.CRITICA, null, null);
		CampanaSeguridad alta = campana("RC-3", GravedadCampana.ALTA, null, null);
		CampanaSeguridad otroRango = campana("RC-4", GravedadCampana.CRITICA, "3N1CK3CD5RL000200", "3N1CK3CD5RL000299");
		when(campanas.delModelo("March", 2025)).thenReturn(List.of(media, critica, alta, otroRango));
		when(delVehiculo.findByVehiculoClienteId(9L)).thenReturn(List.of(delAuto(alta, 40L)));
		Cita confirmada = mock(Cita.class);
		when(confirmada.getEstado()).thenReturn(EstadoCita.CONFIRMADA);
		when(citas.findById(40L)).thenReturn(Optional.of(confirmada));

		List<CampanaApp> lista = servicio.campanas(3L, 9L);

		assertThat(lista).extracting(CampanaApp::codigo).containsExactly("RC-2", "RC-1", "RC-3");
		assertThat(lista).extracting(CampanaApp::estado).containsExactly(EstadoCampana.ABIERTA, EstadoCampana.ABIERTA,
				EstadoCampana.AGENDADA);
		assertThat(lista.get(2).citaId()).isEqualTo(40L);
	}

	@Test
	void unaAgendadaConLaCitaCanceladaVuelveAEstarAbierta() {
		CampanaSeguridad critica = campana("RC-2", GravedadCampana.CRITICA, null, null);
		CampanaVehiculo agendada = delAuto(critica, 40L);
		when(campanas.delModelo("March", 2025)).thenReturn(List.of(critica));
		when(delVehiculo.findByVehiculoClienteId(9L)).thenReturn(List.of(agendada));
		Cita cancelada = mock(Cita.class);
		when(cancelada.getEstado()).thenReturn(EstadoCita.CANCELADA);
		when(citas.findById(40L)).thenReturn(Optional.of(cancelada));

		assertThat(servicio.campanas(3L, 9L)).singleElement().satisfies(campana -> {
			assertThat(campana.estado()).isEqualTo(EstadoCampana.ABIERTA);
			assertThat(campana.citaId()).isNull();
		});
	}

	@Test
	void agendarUsaLaAgendaConMotivoCampanaYLaDejaAgendada() {
		CampanaSeguridad critica = campana("RC-2", GravedadCampana.CRITICA, null, null);
		CampanaVehiculo abierta = delAuto(critica, null);
		when(campanas.delModelo("March", 2025)).thenReturn(List.of(critica));
		when(delVehiculo.findByVehiculoClienteId(9L)).thenReturn(List.of(abierta));
		when(delVehiculo.findByCampanaSeguridadIdAndVehiculoClienteId(critica.getId(), 9L))
				.thenReturn(Optional.of(abierta));
		CitaApp cita = new CitaApp(40L, 9L, "Nissan March 2025", 6L, DIA, LocalTime.of(9, 0), LocalTime.of(10, 0),
				EstadoCita.CONFIRMADA, MotivoCita.CAMPANA, null);
		when(agenda.agendar(3L, 9L, 6L, DIA, MotivoCita.CAMPANA)).thenReturn(cita);

		assertThat(servicio.agendar(3L, 9L, critica.getId(), 6L, DIA)).isSameAs(cita);
		assertThat(abierta.getEstado()).isEqualTo(EstadoCampana.AGENDADA);
		assertThat(abierta.getCitaId()).isEqualTo(40L);
	}

	@Test
	void unaCampanaYaAgendadaOAjenaNoSeAgenda() {
		CampanaSeguridad critica = campana("RC-2", GravedadCampana.CRITICA, null, null);
		CampanaVehiculo agendada = delAuto(critica, 40L);
		when(campanas.delModelo("March", 2025)).thenReturn(List.of(critica));
		when(delVehiculo.findByVehiculoClienteId(9L)).thenReturn(List.of(agendada));
		when(delVehiculo.findByCampanaSeguridadIdAndVehiculoClienteId(critica.getId(), 9L))
				.thenReturn(Optional.of(agendada));
		Cita confirmada = mock(Cita.class);
		when(confirmada.getEstado()).thenReturn(EstadoCita.CONFIRMADA);
		when(citas.findById(40L)).thenReturn(Optional.of(confirmada));

		assertThatThrownBy(() -> servicio.agendar(3L, 9L, critica.getId(), 6L, DIA))
				.isInstanceOf(ConflictoException.class).extracting("codigo")
				.isEqualTo(CodigosError.CAMPANA_NO_AGENDABLE);
		assertThatThrownBy(() -> servicio.agendar(4L, 9L, critica.getId(), 6L, DIA))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.agendar(3L, 9L, 999L, 6L, DIA))
				.isInstanceOf(RecursoNoEncontradoException.class);
		verify(agenda, never()).agendar(any(), any(), any(), any(), any());
	}

	@Test
	void publicarCruzaPorRangoDeVinYAvisaACadaDueno() {
		when(campanas.save(any(CampanaSeguridad.class))).thenAnswer(invocacion -> {
			CampanaSeguridad guardada = invocacion.getArgument(0);
			ReflectionTestUtils.setField(guardada, "id", 55L);
			return guardada;
		});
		VehiculoDeCliente fuera = vehiculo(10L, 4L, "3N1CK3CD5RL000250");
		when(vehiculos.delModelo("March", 2024, 2026)).thenReturn(List.of(MARCH, fuera));

		CampanaPublicadaApp publicada = servicio
				.publicar(new CampanaSeguridad.Datos("rc-9", "Bolsa de aire", "Descripción", "Qué hacer",
						GravedadCampana.CRITICA, "March", 2024, 2026, "3N1CK3CD5RL000100", "3N1CK3CD5RL000199"));

		assertThat(publicada).isEqualTo(new CampanaPublicadaApp(55L, "RC-9", 1));
		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(eventos).publishEvent(evento.capture());
		AvisoAlCliente aviso = (AvisoAlCliente) evento.getValue();
		assertThat(aviso.clienteId()).isEqualTo(3L);
		assertThat(aviso.evento()).isEqualTo("campana.nueva");
		assertThat(aviso.datos()).containsEntry("vehiculoId", 9L).containsEntry("campanaId", 55L)
				.containsEntry("codigo", "RC-9").containsEntry("gravedad", "CRITICA").containsEntry("critica", true);
		ArgumentCaptor<CampanaVehiculo> guardada = ArgumentCaptor.forClass(CampanaVehiculo.class);
		verify(delVehiculo).save(guardada.capture());
		assertThat(guardada.getValue().getAvisadaEn()).isEqualTo(AHORA);
	}

	@Test
	void unCodigoRepetidoNoSePublica() {
		when(campanas.existsByCodigo("RC-9")).thenReturn(true);

		assertThatThrownBy(() -> servicio.publicar(new CampanaSeguridad.Datos("RC-9", "T", "D", "Q",
				GravedadCampana.BAJA, "March", 2024, 2026, null, null))).isInstanceOf(IllegalArgumentException.class);
		verify(campanas, never()).save(any());
		verify(eventos, never()).publishEvent(any(Object.class));
	}
}
