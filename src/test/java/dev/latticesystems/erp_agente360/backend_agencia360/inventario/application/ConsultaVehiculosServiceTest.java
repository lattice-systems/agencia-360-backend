package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultaVehiculosServiceTest {

	@Mock
	private VehiculoRepository vehiculoRepository;

	private ConsultaVehiculosService service;

	@BeforeEach
	void setUp() {
		service = new ConsultaVehiculosService(vehiculoRepository);
	}

	@Test
	void sinFiltroListaTodasLasUnidades() {
		when(vehiculoRepository.findAllByOrderByCreatedAtDescIdDesc())
				.thenReturn(List.of(vehiculo("3VWFE21C04M000002"), vehiculo("3VWFE21C04M000001")));

		assertThat(service.listar(null)).extracting(VehiculoVista::vin).containsExactly("3VWFE21C04M000002",
				"3VWFE21C04M000001");
	}

	@Test
	void filtraPorEstatus() {
		when(vehiculoRepository.findByEstatusOrderByCreatedAtDescIdDesc(EstatusVehiculo.DISPONIBLE))
				.thenReturn(List.of(vehiculo("3VWFE21C04M000001")));

		assertThat(service.listar(EstatusVehiculo.DISPONIBLE)).extracting(VehiculoVista::vin)
				.containsExactly("3VWFE21C04M000001");
	}

	@Test
	void obtieneUnaUnidadPorId() {
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo("3VWFE21C04M000001")));

		assertThat(service.obtener(1L).vin()).isEqualTo("3VWFE21C04M000001");
	}

	@Test
	void unaUnidadQueNoExisteResponde404() {
		when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el vehículo 99");
	}

	private static Vehiculo vehiculo(String vin) {
		return Vehiculo.registrar(vin, "Jetta", "Comfortline", 2026, "Blanco", null);
	}
}
