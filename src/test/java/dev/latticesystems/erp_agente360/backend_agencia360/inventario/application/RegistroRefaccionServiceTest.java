package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistroRefaccionServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");
	private static final UsuarioAutenticado ALMACEN = new UsuarioAutenticado(4L, "almacen1", Set.of(Role.INVENTARIO));

	@Mock
	private RefaccionRepository refaccionRepository;

	@Mock
	private MovimientoInventarioRepository movimientoRepository;

	private RegistroRefaccionService service;

	@BeforeEach
	void setUp() {
		service = new RegistroRefaccionService(refaccionRepository, movimientoRepository,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void sinExistenciaInicialRegistraLaRefaccionSinMovimientos() {
		guardarSinCambios();

		RefaccionVista vista = service.registrar(nueva(" fil-ace-01 ", 0), ALMACEN);

		assertThat(vista.codigo()).isEqualTo("FIL-ACE-01");
		assertThat(vista.descripcion()).isEqualTo("Filtro de aceite");
		assertThat(vista.costo()).isEqualByComparingTo("180.50");
		assertThat(vista.precio()).isEqualByComparingTo("320.00");
		assertThat(vista.existencia()).isZero();
		assertThat(vista.minimo()).isEqualTo(5);
		verify(movimientoRepository, never()).save(any());
	}

	@Test
	void laExistenciaInicialEntraComoPrimeraRecepcion() {
		guardarSinCambios();

		RefaccionVista vista = service.registrar(nueva("FIL-ACE-01", 12), ALMACEN);

		assertThat(vista.existencia()).isEqualTo(12);
		ArgumentCaptor<MovimientoInventario> movimiento = ArgumentCaptor.forClass(MovimientoInventario.class);
		verify(movimientoRepository).save(movimiento.capture());
		assertThat(movimiento.getValue().getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(movimiento.getValue().getRefaccion().getCodigo()).isEqualTo("FIL-ACE-01");
		assertThat(movimiento.getValue().getCantidad()).isEqualTo(12);
		assertThat(movimiento.getValue().getResponsableId()).isEqualTo(4L);
		assertThat(movimiento.getValue().getFecha()).isEqualTo(AHORA);
	}

	@Test
	void rechazaUnaExistenciaInicialNegativa() {
		assertThatThrownBy(() -> service.registrar(nueva("FIL-ACE-01", -1), ALMACEN))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La existencia no puede ser negativa");
		verify(refaccionRepository, never()).save(any());
		verify(movimientoRepository, never()).save(any());
	}

	@Test
	void rechazaUnCodigoQueYaExiste() {
		when(refaccionRepository.existsByCodigo("FIL-ACE-01")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(nueva("fil-ace-01", 3), ALMACEN))
				.isInstanceOf(ConflictoException.class).hasMessage("Ya existe una refacción con el código FIL-ACE-01");
		verify(refaccionRepository, never()).save(any());
		verify(movimientoRepository, never()).save(any());
	}

	private void guardarSinCambios() {
		when(refaccionRepository.save(any(Refaccion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	private static NuevaRefaccion nueva(String codigo, int existencia) {
		return new NuevaRefaccion(codigo, "Filtro de aceite", new BigDecimal("180.50"), new BigDecimal("320.00"),
				existencia, 5);
	}
}
