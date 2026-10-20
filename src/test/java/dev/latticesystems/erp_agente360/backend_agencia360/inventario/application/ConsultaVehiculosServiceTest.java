package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ConsultaVehiculosServiceTest {

	private static final BigDecimal PRECIO = new BigDecimal("389900.00");

	private static final UsuarioAutenticado ALMACEN = actor(Role.INVENTARIO);
	private static final UsuarioAutenticado ADMINISTRADOR = actor(Role.ADMINISTRADOR);
	private static final UsuarioAutenticado VENDEDOR = actor(Role.VENTAS);

	@Mock
	private VehiculoRepository vehiculoRepository;

	private ConsultaVehiculosService service;

	@BeforeEach
	void setUp() {
		service = new ConsultaVehiculosService(vehiculoRepository);
	}

	@Test
	void inventarioYAdministradorVenTodasLasUnidades() {
		when(vehiculoRepository.findAllByOrderByCreatedAtDescIdDesc())
				.thenReturn(List.of(vehiculo("3VWFE21C04M000002"), vehiculo("3VWFE21C04M000001")));

		assertThat(service.listar(null, ALMACEN)).extracting(VehiculoVista::vin).containsExactly("3VWFE21C04M000002",
				"3VWFE21C04M000001");
		assertThat(service.listar(null, ADMINISTRADOR)).hasSize(2);
	}

	@Test
	void filtraPorEstatus() {
		when(vehiculoRepository.findByEstatusOrderByCreatedAtDescIdDesc(EstatusVehiculo.NO_DISPONIBLE))
				.thenReturn(List.of(vehiculo("3VWFE21C04M000001")));

		assertThat(service.listar(EstatusVehiculo.NO_DISPONIBLE, ADMINISTRADOR)).extracting(VehiculoVista::vin)
				.containsExactly("3VWFE21C04M000001");
	}

	@Test
	void ventasSoloVeLasUnidadesDisponibles() {
		when(vehiculoRepository.findByEstatusOrderByCreatedAtDescIdDesc(EstatusVehiculo.DISPONIBLE))
				.thenReturn(List.of(vehiculo("3VWFE21C04M000001")));

		assertThat(service.listar(null, VENDEDOR)).extracting(VehiculoVista::vin).containsExactly("3VWFE21C04M000001");
		assertThat(service.listar(EstatusVehiculo.DISPONIBLE, VENDEDOR)).hasSize(1);
	}

	@Test
	void ventasNoVeOtrosEstatusAunqueLosPida() {
		assertThat(service.listar(EstatusVehiculo.VENDIDO, VENDEDOR)).isEmpty();
		verifyNoInteractions(vehiculoRepository);
	}

	@Test
	void obtieneUnaUnidadPorId() {
		when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo("3VWFE21C04M000001")));

		assertThat(service.obtener(1L, ADMINISTRADOR).vin()).isEqualTo("3VWFE21C04M000001");
	}

	@Test
	void paraVentasUnaUnidadNoDisponibleNoExiste() {
		when(vehiculoRepository.findByIdAndEstatus(1L, EstatusVehiculo.DISPONIBLE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.obtener(1L, VENDEDOR)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el vehículo 1");
	}

	@Test
	void unaUnidadQueNoExisteResponde404() {
		when(vehiculoRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.obtener(99L, ALMACEN)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el vehículo 99");
	}

	@Test
	void otrosRolesNoConsultanUnidades() {
		assertThatThrownBy(() -> service.listar(null, actor(Role.CAJA))).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(vehiculoRepository);
	}

	private static UsuarioAutenticado actor(Role rol) {
		return new UsuarioAutenticado(1L, "usuario", Set.of(rol));
	}

	private static Vehiculo vehiculo(String vin) {
		return Vehiculo.registrar(vin, "Jetta", "Comfortline", 2026, "Blanco", PRECIO, null);
	}
}
