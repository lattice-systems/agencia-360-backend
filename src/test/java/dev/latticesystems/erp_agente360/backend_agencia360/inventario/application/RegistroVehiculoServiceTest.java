package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
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
class RegistroVehiculoServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");
	private static final UsuarioAutenticado ALMACEN = new UsuarioAutenticado(4L, "almacen1", Set.of(Role.INVENTARIO));
	private static final NuevoVehiculo NUEVO = new NuevoVehiculo(" 3vwfe21c04m000001", "Jetta", "Comfortline", 2026,
			"Blanco", new BigDecimal("389900.00"));

	@Mock
	private VehiculoRepository vehiculoRepository;

	@Mock
	private MovimientoInventarioRepository movimientoRepository;

	private RegistroVehiculoService service;

	@BeforeEach
	void setUp() {
		service = new RegistroVehiculoService(vehiculoRepository, movimientoRepository,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void registraLaUnidadDisponible() {
		when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		VehiculoVista vista = service.registrar(NUEVO, ALMACEN);

		assertThat(vista.vin()).isEqualTo("3VWFE21C04M000001");
		assertThat(vista.modelo()).isEqualTo("Jetta");
		assertThat(vista.precio()).isEqualByComparingTo("389900.00");
		assertThat(vista.estatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
	}

	@Test
	void dejaLaEntradaAlInventarioACargoDeQuienRegistra() {
		when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		service.registrar(NUEVO, ALMACEN);

		ArgumentCaptor<MovimientoInventario> movimiento = ArgumentCaptor.forClass(MovimientoInventario.class);
		verify(movimientoRepository).save(movimiento.capture());
		assertThat(movimiento.getValue().getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(movimiento.getValue().getVehiculo().getVin()).isEqualTo("3VWFE21C04M000001");
		assertThat(movimiento.getValue().getCantidad()).isEqualTo(1);
		assertThat(movimiento.getValue().getResponsableId()).isEqualTo(4L);
		assertThat(movimiento.getValue().getFechaRegistro()).isEqualTo(AHORA);
	}

	@Test
	void rechazaUnVinQueYaExiste() {
		when(vehiculoRepository.existsByVin("3VWFE21C04M000001")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(NUEVO, ALMACEN)).isInstanceOf(ConflictoException.class)
				.hasMessage("Ya existe un vehículo con el VIN 3VWFE21C04M000001");
		verify(vehiculoRepository, never()).save(any());
		verify(movimientoRepository, never()).save(any());
	}

	@Test
	void noGuardaNadaSiLosDatosSonInvalidos() {
		NuevoVehiculo sinPrecio = new NuevoVehiculo("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco",
				BigDecimal.ZERO);

		assertThatThrownBy(() -> service.registrar(sinPrecio, ALMACEN)).isInstanceOf(IllegalArgumentException.class);
		verify(vehiculoRepository, never()).save(any());
		verify(movimientoRepository, never()).save(any());
	}
}
