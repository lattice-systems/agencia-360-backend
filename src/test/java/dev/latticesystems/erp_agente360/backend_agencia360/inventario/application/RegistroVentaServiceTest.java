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
class RegistroVentaServiceTest {

	private static final Instant ELEGIDA = Instant.parse("2026-10-19T16:00:00Z");
	private static final Instant ENTREGA = Instant.parse("2026-10-20T18:00:00Z");
	private static final ProspectoFinalizado FINALIZADO = new ProspectoFinalizado(7L, 9L, "Luis Herrera", null,
			ENTREGA);

	@Mock
	private VentaRepository ventaRepository;

	@Mock
	private MovimientoInventarioRepository movimientoRepository;

	@Mock
	private ApplicationEventPublisher eventos;

	private RegistroVentaService service;

	@BeforeEach
	void setUp() {
		service = new RegistroVentaService(ventaRepository, movimientoRepository, eventos);
	}

	@Test
	void alFinalizarRegistraLaVentaYLaUnidadSaleDelInventarioACargoDelVendedor() {
		Vehiculo sentra = Vehiculo.registrar("3N1AB8CV5PY123456", "Sentra", "Advance", 2026, "Blanco",
				new BigDecimal("489900.00"), null);
		ReflectionTestUtils.setField(sentra, "id", 3L);
		Venta venta = Venta.asignar(7L, sentra, 5L, ELEGIDA);
		when(ventaRepository.findByProspectoId(7L)).thenReturn(Optional.of(venta));

		service.alFinalizar(FINALIZADO);

		assertThat(venta.getEstatus()).isEqualTo(EstatusVenta.REGISTRADA);
		assertThat(venta.getRegistradaEn()).isEqualTo(ENTREGA);
		ArgumentCaptor<MovimientoInventario> salida = ArgumentCaptor.forClass(MovimientoInventario.class);
		verify(movimientoRepository).save(salida.capture());
		assertThat(salida.getValue().getTipo()).isEqualTo(TipoMovimiento.SALIDA);
		assertThat(salida.getValue().getVehiculo()).isSameAs(sentra);
		assertThat(salida.getValue().getResponsableId()).isEqualTo(9L);
		assertThat(salida.getValue().getFechaRegistro()).isEqualTo(ENTREGA);
		verify(eventos).publishEvent(new VehiculoCambiado(3L));
	}

	@Test
	void siElEventoLlegaDosVecesNoRegistraOtraSalida() {
		Venta venta = Venta.asignar(7L, Vehiculo.registrar("3N1AB8CV5PY123456", "Sentra", "Advance", 2026, "Blanco",
				new BigDecimal("489900.00"), null), 5L, ELEGIDA);
		venta.registrar(ENTREGA);
		when(ventaRepository.findByProspectoId(7L)).thenReturn(Optional.of(venta));

		service.alFinalizar(FINALIZADO);

		verify(movimientoRepository, never()).save(any());
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void sinUnidadElegidaRegistraLaVentaSinVehiculo() {
		when(ventaRepository.findByProspectoId(7L)).thenReturn(Optional.empty());

		service.alFinalizar(FINALIZADO);

		ArgumentCaptor<Venta> guardada = ArgumentCaptor.forClass(Venta.class);
		verify(ventaRepository).save(guardada.capture());
		assertThat(guardada.getValue().getProspectoId()).isEqualTo(7L);
		assertThat(guardada.getValue().getEstatus()).isEqualTo(EstatusVenta.REGISTRADA);
		assertThat(guardada.getValue().getVehiculo()).isNull();
		verify(movimientoRepository, never()).save(any());
	}
}
