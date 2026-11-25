package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RecordatorioMantenimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.LecturaKmRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RecordatorioMantenimientoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecordatoriosMantenimientoServiceTest {

	private final LecturaKmRepository lecturas = mock(LecturaKmRepository.class);
	private final VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
	private final MantenimientoAppService mantenimiento = mock(MantenimientoAppService.class);
	private final RecordatorioMantenimientoRepository recordatorios = mock(RecordatorioMantenimientoRepository.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private RecordatoriosMantenimientoService servicio;

	@BeforeEach
	void preparar() {
		servicio = new RecordatoriosMantenimientoService(lecturas, vehiculos, mantenimiento, recordatorios, eventos,
				Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC),
				new PoliticaRecordatorioMantenimiento(1000, 14));
		when(vehiculos.buscarPorId(9L)).thenReturn(Optional.of(new VehiculoDeCliente(9L, "VIN", "Nissan", "Versa",
				2024, null, null, 19_500, null, new Propietario(3L, "Ana", null, null))));
	}

	private void conProximo(ProximoServicioApp proximo) {
		when(mantenimiento.plan(3L, 9L)).thenReturn(new PlanMantenimientoApp(9L, 19_500, LocalDate.of(2026, 11, 20),
				"cliente", BigDecimal.TEN, List.of(), proximo));
	}

	@Test
	void avisaAlClienteCuandoSeAcercaElServicio() {
		conProximo(new ProximoServicioApp(5L, "Servicio 20 000 km", 20_000, LocalDate.of(2026, 12, 20), 500, 25, "km"));
		assertThat(servicio.revisar(9L)).isTrue();

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(eventos).publishEvent(evento.capture());
		AvisoAlCliente aviso = (AvisoAlCliente) evento.getValue();
		assertThat(aviso.clienteId()).isEqualTo(3L);
		assertThat(aviso.evento()).isEqualTo("mantenimiento.recordatorio");
		assertThat(aviso.datos()).containsEntry("vehiculoId", 9L).containsEntry("motivo", "km");
		verify(recordatorios).saveAndFlush(any(RecordatorioMantenimiento.class));
	}

	@Test
	void noRepiteUnServicioYaAvisado() {
		conProximo(new ProximoServicioApp(5L, "Servicio 20 000 km", 20_000, LocalDate.of(2026, 12, 20), 500, 25, "km"));
		when(recordatorios.existsByVehiculoClienteIdAndPlanMantenimientoId(9L, 5L)).thenReturn(true);
		assertThat(servicio.revisar(9L)).isFalse();
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void noAvisaSiTodaviaFaltaMucho() {
		conProximo(new ProximoServicioApp(5L, "Servicio 20 000 km", 20_000, LocalDate.of(2027, 3, 1), 4000, 100, "km"));
		assertThat(servicio.revisar(9L)).isFalse();
		verify(recordatorios, never()).saveAndFlush(any());
	}

	@Test
	void unVehiculoQueYaNoExisteSeIgnora() {
		when(vehiculos.buscarPorId(77L)).thenReturn(Optional.empty());
		assertThat(servicio.revisar(77L)).isFalse();
	}

	@Test
	void unFalloEnUnVehiculoNoDetieneLaRevision() {
		when(lecturas.vehiculosConLecturas()).thenReturn(List.of(9L, 10L));
		when(mantenimiento.plan(3L, 9L)).thenThrow(new IllegalStateException("datos raros"));
		servicio.revisarTodos();
		verify(vehiculos).buscarPorId(10L);
	}
}
