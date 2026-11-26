package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** V2-M26.05: un punto por mes con la última estimación de ese mes. */
@ExtendWith(MockitoExtension.class)
class ValorEstimadoServiceTest {

	@Mock
	private EstimacionTomaCuentaRepository estimaciones;
	@Mock
	private VehiculosDeClientes vehiculos;

	private ValorEstimadoService servicio;

	@BeforeEach
	void preparar() {
		servicio = new ValorEstimadoService(estimaciones, vehiculos,
				Clock.fixed(Instant.parse("2026-11-25T18:00:00Z"), AgendaAppService.ZONA_AGENCIA));
		VehiculoDeCliente vehiculo = mock(VehiculoDeCliente.class, Answers.RETURNS_DEEP_STUBS);
		lenient().when(vehiculo.propietario().id()).thenReturn(1L);
		lenient().when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(vehiculo));
	}

	@Test
	void usaLaMasRecienteDeCadaMesYNoInventaLosVacios() {
		guardadas(estimacion(1L, "2026-08-10T12:00:00Z", "100000", "120000"),
				estimacion(2L, "2026-11-02T12:00:00Z", "90000", "100000"),
				estimacion(3L, "2026-11-20T12:00:00Z", "90000.01", "100000"));

		ValorEstimadoApp valor = servicio.porMes(1L, 3L, null);

		assertThat(valor.vehiculoId()).isEqualTo(3L);
		assertThat(valor.puntos()).extracting(ValorEstimadoApp.Punto::mes).containsExactly("2026-08", "2026-11");
		assertThat(valor.puntos()).extracting(ValorEstimadoApp.Punto::estimacionTomaCuentaId).containsExactly(1L, 3L);
		assertThat(valor.puntos().get(0).medio()).isEqualByComparingTo("110000.00");
		// Promedio con 2 decimales.
		assertThat(valor.puntos().get(1).medio()).isEqualTo(new BigDecimal("95000.01"));
	}

	@Test
	void elMesSeCuentaEnLaHoraDeLaAgencia() {
		// El 1 de noviembre a las 03:00 UTC todavía es 31 de octubre en la agencia.
		guardadas(estimacion(1L, "2026-11-01T03:00:00Z", "1", "3"), estimacion(2L, "2026-11-01T07:00:00Z", "1", "3"));

		assertThat(servicio.porMes(1L, 3L, 2).puntos()).extracting(ValorEstimadoApp.Punto::mes)
				.containsExactly("2026-10", "2026-11");
	}

	@Test
	void pideDesdeElPrimerDiaDelMesMasAntiguoContandoElActual() {
		servicio.porMes(1L, 3L, null);
		servicio.porMes(1L, 3L, 1);

		verify(estimaciones).delVehiculoDesde(1L, 3L, Instant.parse("2025-12-01T06:00:00Z"));
		verify(estimaciones).delVehiculoDesde(1L, 3L, Instant.parse("2026-11-01T06:00:00Z"));
	}

	@Test
	void rechazaUnRangoDeMesesInvalidoYUnVehiculoAjeno() {
		assertThatThrownBy(() -> servicio.porMes(1L, 3L, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.porMes(1L, 3L, 37)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.porMes(2L, 3L, null)).isInstanceOf(RecursoNoEncontradoException.class);
		when(vehiculos.buscarPorId(9L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> servicio.porMes(1L, 9L, null)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	private void guardadas(EstimacionTomaCuenta... lista) {
		when(estimaciones.delVehiculoDesde(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(3L),
				org.mockito.ArgumentMatchers.any())).thenReturn(List.of(lista));
	}

	private static EstimacionTomaCuenta estimacion(long id, String creada, String minimo, String maximo) {
		EstimacionTomaCuenta estimacion = mock(EstimacionTomaCuenta.class);
		lenient().when(estimacion.getId()).thenReturn(id);
		lenient().when(estimacion.getCreatedAt()).thenReturn(Instant.parse(creada));
		lenient().when(estimacion.getMinimo()).thenReturn(new BigDecimal(minimo));
		lenient().when(estimacion.getMaximo()).thenReturn(new BigDecimal(maximo));
		lenient().when(estimacion.getKm()).thenReturn(1000);
		lenient().when(estimacion.getEstado()).thenReturn(EstadoVehiculo.BUENO);
		return estimacion;
	}
}
