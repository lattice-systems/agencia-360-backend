package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Canje;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCanje;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.NivelLealtad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Recompensa;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CanjeRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.MovimientoPuntosRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RecompensaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.DetalleDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class LealtadAppServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");

	private final MovimientoPuntosRepository movimientos = mock(MovimientoPuntosRepository.class);
	private final RecompensaRepository recompensas = mock(RecompensaRepository.class);
	private final CanjeRepository canjes = mock(CanjeRepository.class);
	private final CuentaPuntosService cuenta = mock(CuentaPuntosService.class);
	private final HistorialDeServicios historial = mock(HistorialDeServicios.class);
	private LealtadAppService servicio;

	@BeforeEach
	void preparar() {
		servicio = new LealtadAppService(movimientos, recompensas, canjes, cuenta, historial,
				ReglasLealtad.porOmision(), TransactionOperations.withoutTransaction(),
				Clock.fixed(AHORA, ZoneOffset.UTC));
		when(canjes.save(any(Canje.class))).thenAnswer(invocacion -> {
			Canje canje = invocacion.getArgument(0);
			ReflectionTestUtils.setField(canje, "id", 90L);
			return canje;
		});
	}

	private static Recompensa recompensa(Long id, int puntos, Integer existencia) {
		Recompensa recompensa = new Recompensa("Lavado exprés", null, puntos, existencia);
		ReflectionTestUtils.setField(recompensa, "id", id);
		return recompensa;
	}

	private static DetalleDelServicio detalle(Long ordenId, String total) {
		return new DetalleDelServicio(ordenId, 9L, LocalDate.of(2026, 11, 25), 20_000, EstatusOrden.ENTREGADO, AHORA,
				List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				new BigDecimal(total));
	}

	@Test
	void elResumenDaSaldoNivelYLoQueFaltaParaElSiguiente() {
		when(movimientos.saldo(3L)).thenReturn(1240L);
		when(movimientos.ganadosDesde(any(), any(), any())).thenReturn(1640L);

		ResumenLealtadApp resumen = servicio.resumen(3L);

		assertThat(resumen.saldo()).isEqualTo(1240);
		assertThat(resumen.nivel()).isEqualTo(NivelLealtad.PLATA);
		assertThat(resumen.siguienteNivel()).isEqualTo(NivelLealtad.ORO);
		assertThat(resumen.puntosParaSiguiente()).isEqualTo(2360);
		assertThat(resumen.codigoReferido()).isEqualTo("CC00003");
		ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
		verify(movimientos).ganadosDesde(any(), any(), desde.capture());
		assertThat(desde.getValue()).isEqualTo(AHORA.minus(Duration.ofDays(365)));
	}

	@Test
	void enOroNoHaySiguienteNivel() {
		when(movimientos.ganadosDesde(any(), any(), any())).thenReturn(9000L);
		ResumenLealtadApp resumen = servicio.resumen(3L);
		assertThat(resumen.nivel()).isEqualTo(NivelLealtad.ORO);
		assertThat(resumen.siguienteNivel()).isNull();
		assertThat(resumen.puntosParaSiguiente()).isZero();
	}

	@Test
	void canjearBloqueaElSaldoAntesDeLeerloYDescuentaLosPuntos() {
		Recompensa lavado = recompensa(7L, 300, 2);
		when(recompensas.bloquear(7L)).thenReturn(Optional.of(lavado));
		when(cuenta.saldo(3L)).thenReturn(300L);

		CanjeApp canje = servicio.canjear(3L, 7L);

		assertThat(canje.codigo()).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
		assertThat(canje.estado()).isEqualTo(EstadoCanje.VIGENTE);
		assertThat(canje.venceEn()).isEqualTo(AHORA.plus(Duration.ofDays(30)));
		assertThat(canje.recompensa()).isEqualTo("Lavado exprés");
		assertThat(lavado.getExistencia()).isEqualTo(1);
		InOrder orden = inOrder(cuenta, recompensas);
		orden.verify(cuenta).bloquear(3L);
		orden.verify(recompensas).bloquear(7L);
		orden.verify(cuenta).saldo(3L);
		orden.verify(cuenta).mover(3L, -300, MotivoPuntos.CANJE, "CANJE", 90L);
	}

	@Test
	void sinSaldoOSinExistenciaNoSeCanjea() {
		when(recompensas.bloquear(7L)).thenReturn(Optional.of(recompensa(7L, 300, null)));
		when(recompensas.bloquear(8L)).thenReturn(Optional.of(recompensa(8L, 100, 0)));
		when(cuenta.saldo(3L)).thenReturn(299L);

		assertThatThrownBy(() -> servicio.canjear(3L, 7L)).isInstanceOf(ConflictoException.class).extracting("codigo")
				.isEqualTo(CodigosError.SALDO_INSUFICIENTE);
		assertThatThrownBy(() -> servicio.canjear(3L, 8L)).isInstanceOf(ConflictoException.class).extracting("codigo")
				.isEqualTo(CodigosError.RECOMPENSA_AGOTADA);
		assertThatThrownBy(() -> servicio.canjear(3L, 99L)).isInstanceOf(RecursoNoEncontradoException.class);
		verify(canjes, never()).save(any());
		verify(cuenta, never()).mover(any(), anyInt(), any(), any(), any());
	}

	@Test
	void unaOrdenEntregadaSumaUnPuntoPorCadaDiezPesos() {
		when(historial.detalle(41L)).thenReturn(Optional.of(detalle(41L, "1650.00")));

		servicio.alCambiarOrden(new OrdenServicioCambiada(41L, 9L, 3L, EstatusOrden.ENTREGADO, EtapaTaller.ENTREGADO,
				MotivoCambio.ETAPA));

		verify(cuenta).mover(3L, 165, MotivoPuntos.SERVICIO, "ORDEN", 41L);
	}

	@Test
	void pagarLaOrdenSumaBajoElCandadoDelSaldo() {
		when(historial.detalle(41L)).thenReturn(Optional.of(detalle(41L, "1650.00")));

		servicio.alConfirmarPago(new PagoConfirmado(7L, 41L, 3L, new BigDecimal("1650.00")));

		InOrder orden = inOrder(cuenta);
		orden.verify(cuenta).bloquear(3L);
		orden.verify(cuenta).mover(3L, 165, MotivoPuntos.SERVICIO, "ORDEN", 41L);
	}

	@Test
	void unaOrdenSinEntregarRegistradaYaHechaOSinClienteNoSuma() {
		when(historial.detalle(anyLong())).thenReturn(Optional.of(detalle(41L, "1650.00")));
		servicio.alCambiarOrden(new OrdenServicioCambiada(41L, 9L, 3L, EstatusOrden.TERMINADO,
				EtapaTaller.LISTO_PARA_ENTREGA, MotivoCambio.ETAPA));
		servicio.alCambiarOrden(
				new OrdenServicioCambiada(41L, 9L, 3L, EstatusOrden.ENTREGADO, null, MotivoCambio.REGISTRO));
		servicio.alCambiarOrden(new OrdenServicioCambiada(41L, 9L, null, EstatusOrden.ENTREGADO, EtapaTaller.ENTREGADO,
				MotivoCambio.ETAPA));
		when(historial.detalle(42L)).thenReturn(Optional.of(detalle(42L, "5.00")));
		servicio.alCambiarOrden(new OrdenServicioCambiada(42L, 9L, 3L, EstatusOrden.ENTREGADO, EtapaTaller.ENTREGADO,
				MotivoCambio.ETAPA));

		verify(cuenta, never()).mover(any(), anyInt(), any(), any(), any());
	}

	@Test
	void elPersonalUsaUnCanjeVigenteSinImportarMayusculas() {
		Canje canje = new Canje(3L, recompensa(7L, 300, null), "K7XQ2MPA", AHORA.minusSeconds(60),
				AHORA.plus(Duration.ofDays(1)));
		when(canjes.findByCodigo("K7XQ2MPA")).thenReturn(Optional.of(canje));
		when(recompensas.findById(7L)).thenReturn(Optional.of(recompensa(7L, 300, null)));

		assertThat(servicio.usar(" k7xq2mpa ").estado()).isEqualTo(EstadoCanje.USADO);
		assertThatThrownBy(() -> servicio.usar("K7XQ2MPA")).isInstanceOf(ConflictoException.class).extracting("codigo")
				.isEqualTo(CodigosError.CANJE_NO_VIGENTE);
		assertThatThrownBy(() -> servicio.usar("NOEXISTE")).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unCanjeVencidoRegresaSusPuntosYSuExistencia() {
		Recompensa tapetes = recompensa(7L, 1500, 0);
		Canje canje = new Canje(3L, tapetes, "K7XQ2MPA", AHORA.minus(Duration.ofDays(31)),
				AHORA.minus(Duration.ofDays(1)));
		ReflectionTestUtils.setField(canje, "id", 90L);
		when(canjes.findByEstadoAndVenceEnLessThanEqualOrderByIdAsc(EstadoCanje.VIGENTE, AHORA))
				.thenReturn(List.of(canje));
		when(canjes.findById(90L)).thenReturn(Optional.of(canje));
		when(recompensas.bloquear(7L)).thenReturn(Optional.of(tapetes));

		servicio.vencerTodos();

		assertThat(canje.getEstado()).isEqualTo(EstadoCanje.VENCIDO);
		assertThat(tapetes.getExistencia()).isEqualTo(1);
		verify(cuenta).bloquear(3L);
		verify(cuenta).mover(3L, 1500, MotivoPuntos.DEVOLUCION_CANJE, "CANJE", 90L);
	}
}
