package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EdicionVehiculoServiceTest {

	@Mock
	private VehiculoRepository vehiculoRepository;

	private EdicionVehiculoService service;

	@BeforeEach
	void setUp() {
		service = new EdicionVehiculoService(vehiculoRepository);
	}

	@Test
	void corrigeLosDatosDeLaUnidad() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", null);
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));

		VehiculoVista vista = service.editar(1L,
				new VehiculoEditado("3vwfe21c04m000009", "Jetta", "GLI", 2026, "Rojo", EstatusVehiculo.NO_DISPONIBLE));

		assertThat(vista.vin()).isEqualTo("3VWFE21C04M000009");
		assertThat(vista.version()).isEqualTo("GLI");
		assertThat(vista.color()).isEqualTo("Rojo");
		assertThat(vista.estatus()).isEqualTo(EstatusVehiculo.NO_DISPONIBLE);
	}

	@Test
	void rechazaUnVinQueYaTieneOtraUnidadSinTocarLaUnidad() {
		Vehiculo vehiculo = Vehiculo.registrar("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", null);
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));
		when(vehiculoRepository.existsByVinAndIdNot("3VWFE21C04M000002", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.editar(1L,
				new VehiculoEditado("3VWFE21C04M000002", "Jetta", "Comfortline", 2026, "Rojo", null)))
				.isInstanceOf(ConflictoException.class)
				.hasMessage("Ya existe un vehículo con el VIN 3VWFE21C04M000002");
		assertThat(vehiculo.getVin()).isEqualTo("3VWFE21C04M000001");
		assertThat(vehiculo.getColor()).isEqualTo("Blanco");
	}

	@Test
	void unaUnidadQueNoExisteResponde404() {
		when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.editar(99L,
				new VehiculoEditado("3VWFE21C04M000001", "Jetta", "Comfortline", 2026, "Blanco", null)))
				.isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe el vehículo 99");
	}
}
