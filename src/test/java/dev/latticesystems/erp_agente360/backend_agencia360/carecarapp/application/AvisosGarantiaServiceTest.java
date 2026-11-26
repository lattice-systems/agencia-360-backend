package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.KilometrajeService.Estado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.AvisoGarantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Garantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.GarantiaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
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
import org.springframework.test.util.ReflectionTestUtils;

class AvisosGarantiaServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");
	private static final VehiculoDeCliente MARCH = new VehiculoDeCliente(9L, "VIN", "Nissan", "March", 2024, null, null,
			20_000, null, new Propietario(3L, "Ana", null, null));

	private final GarantiaRepository garantias = mock(GarantiaRepository.class);
	private final VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
	private final KilometrajeService kilometraje = mock(KilometrajeService.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private AvisosGarantiaService servicio;

	@BeforeEach
	void preparar() {
		servicio = new AvisosGarantiaService(garantias, vehiculos, kilometraje, eventos,
				Clock.fixed(AHORA, ZoneOffset.UTC), ZoneOffset.UTC);
		when(vehiculos.buscarPorId(9L)).thenReturn(Optional.of(MARCH));
	}

	private Garantia garantia(Long id, LocalDate vence, Integer kmLimite) {
		Garantia garantia = Garantia.registrar(9L, "Motor", null, LocalDate.of(2024, 1, 1), vence, kmLimite);
		ReflectionTestUtils.setField(garantia, "id", id);
		when(garantias.findById(id)).thenReturn(Optional.of(garantia));
		return garantia;
	}

	private void conKm(Integer km) {
		when(kilometraje.estado(MARCH)).thenReturn(new Estado(km, null, null, null, List.of()));
	}

	@Test
	void avisaA15DiasUnaSolaVez() {
		Garantia motor = garantia(1L, LocalDate.of(2026, 12, 5), null);
		conKm(20_000);

		assertThat(servicio.revisar(1L)).isTrue();
		assertThat(servicio.revisar(1L)).isFalse();

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(eventos).publishEvent(evento.capture());
		AvisoAlCliente aviso = (AvisoAlCliente) evento.getValue();
		assertThat(aviso.clienteId()).isEqualTo(3L);
		assertThat(aviso.evento()).isEqualTo("garantia.por_vencer");
		assertThat(aviso.datos()).containsEntry("vehiculoId", 9L).containsEntry("garantiaId", 1L)
				.containsEntry("componente", "Motor").containsEntry("venceEn", "2026-12-05")
				.containsEntry("diasRestantes", 10L).containsEntry("motivo", "dias_15")
				.doesNotContainKey("kmRestantes");
		assertThat(motor.avisado(AvisoGarantia.DIAS_60)).isTrue();
		verify(garantias).saveAndFlush(motor);
	}

	@Test
	void avisaA1000KmDelLimite() {
		garantia(2L, LocalDate.of(2027, 6, 1), 20_800);
		conKm(20_000);

		assertThat(servicio.revisar(2L)).isTrue();

		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(eventos).publishEvent(evento.capture());
		assertThat(((AvisoAlCliente) evento.getValue()).datos()).containsEntry("motivo", "km_1000")
				.containsEntry("kmRestantes", 800);
	}

	@Test
	void lejosDelLimiteOVencidaNoAvisa() {
		garantia(3L, LocalDate.of(2027, 6, 1), 60_000);
		garantia(4L, LocalDate.of(2026, 11, 1), null);
		conKm(20_000);

		assertThat(servicio.revisar(3L)).isFalse();
		assertThat(servicio.revisar(4L)).isFalse();
		assertThat(servicio.revisar(99L)).isFalse();
		verify(eventos, never()).publishEvent(any(Object.class));
		verify(garantias, never()).saveAndFlush(any());
	}

	@Test
	void unFalloEnUnaGarantiaNoDetieneLaRevision() {
		Garantia rota = garantia(5L, LocalDate.of(2026, 12, 5), null);
		Garantia buena = garantia(6L, LocalDate.of(2026, 12, 5), null);
		ReflectionTestUtils.setField(rota, "vehiculoClienteId", 77L);
		when(vehiculos.buscarPorId(77L)).thenThrow(new IllegalStateException("datos raros"));
		when(garantias.vigentes(LocalDate.of(2026, 11, 25))).thenReturn(List.of(rota, buena));
		conKm(20_000);

		servicio.revisarTodas();

		verify(eventos).publishEvent(any(Object.class));
	}
}
