package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVenta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Venta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SalidaAlEntregarServiceTest {

	private static final Instant EMISION = Instant.parse("2026-10-24T16:00:00Z");
	private static final Instant ENTREGA = Instant.parse("2026-10-25T18:00:00Z");
	private static final ProspectoFinalizado FINALIZADO = new ProspectoFinalizado(7L, 9L, "Luis Herrera", null,
			ENTREGA);

	@Mock
	private VentaRepository ventaRepository;

	@Mock
	private MovimientoInventarioRepository movimientoRepository;

	@Mock
	private ApplicationEventPublisher eventos;

	private SalidaAlEntregarService service;

	@BeforeEach
	void setUp() {
		service = new SalidaAlEntregarService(ventaRepository, movimientoRepository, eventos);
	}

	@Test
	void alFinalizarLaUnidadSaleDelInventarioACargoDelVendedor() {
		Venta venta = ventaDelSentra();
		when(ventaRepository.findByProspectoId(7L)).thenReturn(Optional.of(venta));

		service.alFinalizar(FINALIZADO);

		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.ENTREGADA);
		assertThat(venta.getEntregadaEn()).isEqualTo(ENTREGA);
		ArgumentCaptor<MovimientoInventario> salida = ArgumentCaptor.forClass(MovimientoInventario.class);
		verify(movimientoRepository).save(salida.capture());
		assertThat(salida.getValue().getTipo()).isEqualTo(TipoMovimiento.SALIDA);
		assertThat(salida.getValue().getResponsableId()).isEqualTo(9L);
		verify(eventos).publishEvent(new VehiculoCambiado(3L));
	}

	@Test
	void siElEventoLlegaDosVecesLaSalidaNoSeRepite() {
		Venta venta = ventaDelSentra();
		venta.entregar(ENTREGA);
		when(ventaRepository.findByProspectoId(7L)).thenReturn(Optional.of(venta));

		service.alFinalizar(FINALIZADO);

		verify(movimientoRepository, never()).save(any());
		verify(eventos, never()).publishEvent(any());
	}

	@Test
	void sinFacturaNoSaleNingunaUnidadNiSeRegistraVenta() {
		when(ventaRepository.findByProspectoId(7L)).thenReturn(Optional.empty());

		service.alFinalizar(FINALIZADO);

		verify(ventaRepository, never()).save(any());
		verify(movimientoRepository, never()).save(any());
	}

	private static Venta ventaDelSentra() {
		Vehiculo sentra = Vehiculo.registrar("3N1AB8CV5PY123456", "Sentra", "Advance", 2026, "Blanco",
				new BigDecimal("489900.00"), null);
		ReflectionTestUtils.setField(sentra, "id", 3L);
		return Venta.registrar(7L, sentra, 5L, EMISION);
	}
}
