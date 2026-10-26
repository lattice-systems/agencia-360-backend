package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.Servicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.ServicioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpedienteServiceTest {

	/** 20:00 del 26 de octubre en la agencia; en UTC ya es 27. */
	private static final Instant AHORA = Instant.parse("2026-10-27T02:00:00Z");
	private static final LocalDate HOY_EN_LA_AGENCIA = LocalDate.of(2026, 10, 26);

	private static final VehiculoDeCliente SENTRA = new VehiculoDeCliente(3L, "3N1AB8CV5RY000001", "Nissan", "Sentra",
			2026, "Advance", "ABC-123-D", 15_000, new Propietario(9L, "Ana López", "ana@correo.com", "4775550101"));

	private static final UsuarioAutenticado TECNICO = new UsuarioAutenticado(7L, "tecnico", Set.of(Role.SERVICIO));

	@Mock
	private VehiculosDeClientes vehiculos;

	@Mock
	private ServicioRepository servicioRepository;

	@Mock
	private DirectorioUsuarios directorioUsuarios;

	private ExpedienteService service;

	@BeforeEach
	void setUp() {
		service = new ExpedienteService(vehiculos, servicioRepository, directorioUsuarios,
				Clock.fixed(AHORA, ExpedienteService.ZONA_AGENCIA));
	}

	private static Servicio servicio(LocalDate fecha, String precio) {
		return Servicio.registrar(3L, fecha, 15_000, 7L,
				List.of(new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, new BigDecimal(precio))),
				HOY_EN_LA_AGENCIA);
	}

	@Test
	void armaElExpedienteConElVehiculoSuDuenoYSusServicios() {
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));
		when(servicioRepository.findByVehiculoClienteIdOrderByFechaDescIdDesc(3L)).thenReturn(
				List.of(servicio(LocalDate.of(2026, 10, 20), "1200"), servicio(LocalDate.of(2026, 5, 2), "800")));
		when(directorioUsuarios.nombresDe(List.of(7L))).thenReturn(Map.of(7L, "Luis Técnico"));

		Expediente expediente = service.expediente(3L);

		assertThat(expediente.vehiculo().marca()).isEqualTo("Nissan");
		assertThat(expediente.vehiculo().version()).isEqualTo("Advance");
		assertThat(expediente.cliente().nombre()).isEqualTo("Ana López");
		assertThat(expediente.servicios()).extracting(ServicioResumen::fecha)
				.containsExactly(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 5, 2));
		assertThat(expediente.servicios()).extracting(ServicioResumen::atendidoPor).containsOnly("Luis Técnico");
	}

	@Test
	void unVehiculoQueNoExisteResponde404() {
		when(vehiculos.buscarPorId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.expediente(99L)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el vehículo 99");
	}

	@Test
	void registraElServicioANombreDeQuienLoAtiendeConElDiaDeLaAgencia() {
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));
		when(servicioRepository.save(any(Servicio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		when(directorioUsuarios.nombresDe(List.of(7L))).thenReturn(Map.of(7L, "Luis Técnico"));

		NuevoServicio nuevo = new NuevoServicio(HOY_EN_LA_AGENCIA, 16_000,
				List.of(new RenglonNuevo(TipoRenglon.REFACCION, "Filtro de aire", 2, new BigDecimal("290.00"))));
		ServicioDetalle detalle = service.registrar(3L, nuevo, TECNICO);

		assertThat(detalle.atendidoPor()).isEqualTo("Luis Técnico");
		assertThat(detalle.vehiculoId()).isEqualTo(3L);
		assertThat(detalle.total()).isEqualByComparingTo("580.00");
		assertThat(detalle.renglones()).singleElement()
				.satisfies(renglon -> assertThat(renglon.importe()).isEqualByComparingTo("580.00"));
	}

	@Test
	void noRegistraUnServicioConFechaDeMananaEnLaAgencia() {
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));

		assertThatThrownBy(() -> service.registrar(3L,
				new NuevoServicio(HOY_EN_LA_AGENCIA.plusDays(1), 16_000,
						List.of(new RenglonNuevo(TipoRenglon.REFACCION, "Filtro", 1, BigDecimal.TEN))),
				TECNICO)).isInstanceOf(IllegalArgumentException.class);
		verify(servicioRepository, never()).save(any());
	}

	@Test
	void noRegistraServiciosDeUnVehiculoQueNoExiste() {
		when(vehiculos.buscarPorId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.registrar(99L,
				new NuevoServicio(HOY_EN_LA_AGENCIA, 100,
						List.of(new RenglonNuevo(TipoRenglon.REFACCION, "Filtro", 1, BigDecimal.TEN))),
				TECNICO)).isInstanceOf(RecursoNoEncontradoException.class);
		verify(servicioRepository, never()).save(any());
	}

	@Test
	void elDetalleDeUnServicioDeOtroVehiculoResponde404() {
		when(servicioRepository.obtenerConRenglones(3L, 50L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detalle(3L, 50L)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("El vehículo 3 no tiene el servicio 50");
	}
}
