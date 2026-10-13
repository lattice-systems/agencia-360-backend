package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecepcionRefaccionServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");
	private static final UsuarioAutenticado ALMACEN = new UsuarioAutenticado(4L, "almacen1", Set.of(Role.INVENTARIO));

	@Mock
	private RefaccionRepository refaccionRepository;

	@Mock
	private MovimientoInventarioRepository movimientoRepository;

	private RecepcionRefaccionService service;

	@BeforeEach
	void setUp() {
		service = new RecepcionRefaccionService(refaccionRepository, movimientoRepository,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void sumaLaCantidadALaExistencia() {
		Refaccion refaccion = refaccionConExistencia(3);
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccion));
		guardarMovimientoSinCambios();

		RecepcionRegistrada recepcion = service.recibir(1L, 10, ALMACEN);

		assertThat(refaccion.getExistencia()).isEqualTo(13);
		assertThat(recepcion.existencia()).isEqualTo(13);
		assertThat(recepcion.cantidad()).isEqualTo(10);
		assertThat(recepcion.codigo()).isEqualTo("FIL-ACE-01");
	}

	@Test
	void guardaLaFechaLaCantidadYElResponsable() {
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccionConExistencia(0)));
		guardarMovimientoSinCambios();

		RecepcionRegistrada recepcion = service.recibir(1L, 10, ALMACEN);

		ArgumentCaptor<MovimientoInventario> movimiento = ArgumentCaptor.forClass(MovimientoInventario.class);
		verify(movimientoRepository).save(movimiento.capture());
		assertThat(movimiento.getValue().getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(movimiento.getValue().getCantidad()).isEqualTo(10);
		assertThat(movimiento.getValue().getResponsableId()).isEqualTo(4L);
		assertThat(movimiento.getValue().getFecha()).isEqualTo(AHORA);
		assertThat(recepcion.responsableId()).isEqualTo(4L);
		assertThat(recepcion.fecha()).isEqualTo(AHORA);
	}

	@Test
	void bloqueaLaFilaDeLaRefaccionAntesDeSumar() {
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccionConExistencia(0)));
		guardarMovimientoSinCambios();

		service.recibir(1L, 1, ALMACEN);

		verify(refaccionRepository).buscarParaActualizar(1L);
		verify(refaccionRepository, never()).findById(anyLong());
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() {
		when(refaccionRepository.buscarParaActualizar(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.recibir(99L, 5, ALMACEN)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe la refacción 99");
		verify(movimientoRepository, never()).save(any());
	}

	@Test
	void noGuardaNadaSiLaCantidadNoEsMayorACero() {
		Refaccion refaccion = refaccionConExistencia(3);
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccion));

		assertThatThrownBy(() -> service.recibir(1L, 0, ALMACEN)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La cantidad debe ser mayor a cero");
		assertThat(refaccion.getExistencia()).isEqualTo(3);
		verify(movimientoRepository, never()).save(any());
	}

	private void guardarMovimientoSinCambios() {
		when(movimientoRepository.save(any(MovimientoInventario.class)))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	private static Refaccion refaccionConExistencia(int existencia) {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", new BigDecimal("180.50"),
				new BigDecimal("320.00"), 5);
		if (existencia > 0) {
			refaccion.recibir(existencia, 1L, AHORA);
		}
		return refaccion;
	}
}
