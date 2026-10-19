package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EdicionVehiculoServiceTest {

	private static final BigDecimal PRECIO = new BigDecimal("389900.00");

	@Mock
	private VehiculoRepository vehiculoRepository;

	@Mock
	private ApplicationEventPublisher eventos;

	private EdicionVehiculoService service;

	@BeforeEach
	void setUp() {
		service = new EdicionVehiculoService(vehiculoRepository, eventos);
	}

	@Test
	void avisaQueCambioElInventarioDeVehiculos() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				null);
		ReflectionTestUtils.setField(vehiculo, "id", 1L);
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));

		service.editar(1L,
				new VehiculoEditado("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Rojo", PRECIO, null));

		verify(eventos).publishEvent(new VehiculoCambiado(1L));
	}

	@Test
	void unaCorreccionRechazadaNoAvisa() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				null);
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));

		assertThatThrownBy(() -> service.editar(1L,
				new VehiculoEditado("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, " ", PRECIO, null)))
				.isInstanceOf(IllegalArgumentException.class);
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void corrigeLosDatosDeLaUnidad() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				null);
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));

		VehiculoVista vista = service.editar(1L, new VehiculoEditado("3vwfe21c04m000009", "Jetta", "GLI", 2026, "Rojo",
				PRECIO, EstatusVehiculo.NO_DISPONIBLE));

		assertThat(vista.vin()).isEqualTo("3VWFE21C04M000009");
		assertThat(vista.version()).isEqualTo("GLI");
		assertThat(vista.color()).isEqualTo("Rojo");
		assertThat(vista.estatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}

	@Test
	void rechazaUnVinQueYaTieneOtraUnidadSinTocarLaUnidad() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO,
				null);
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));
		when(vehiculoRepository.existsByVinAndIdNot("3VWFE21C04M000002", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.editar(1L,
				new VehiculoEditado("3VWFE21C04M000002", "Jetta", "Comfortline", 2026, "Rojo", PRECIO, null)))
				.isInstanceOf(ConflictoException.class)
				.hasMessage("Ya existe un vehículo con el VIN 3VWFE21C04M000002");
		assertThat(vehiculo.getVin()).isEqualTo("3VWFE21C04M000001");
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
	}

	@Test
	void unaUnidadQueNoExisteResponde404() {
		when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.editar(99L,
				new VehiculoEditado("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null)))
				.isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe el vehículo 99");
	}
}
