package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GastoVehiculoTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 20);
	private static final Instant AHORA = Instant.parse("2026-11-20T18:00:00Z");

	@Test
	void unaCargaDeGasolinaGuardaLitrosYKm() {
		GastoVehiculo gasto = GastoVehiculo.registrar(7L, TipoGasto.GASOLINA, new BigDecimal("850.50"),
				new BigDecimal("35.2"), 45210, HOY, null, HOY, AHORA);

		assertThat(gasto.getVehiculoClienteId()).isEqualTo(7L);
		assertThat(gasto.getTipo()).isEqualTo(TipoGasto.GASOLINA);
		assertThat(gasto.getLitros()).isEqualByComparingTo("35.2");
		assertThat(gasto.getKm()).isEqualTo(45210);
		assertThat(gasto.getFecha()).isEqualTo(HOY);
	}

	@Test
	void gasolinaExigeLitrosYKm() {
		assertThatThrownBy(
				() -> GastoVehiculo.registrar(7L, TipoGasto.GASOLINA, BigDecimal.TEN, null, 100, HOY, null, HOY, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("litros");
		assertThatThrownBy(() -> GastoVehiculo.registrar(7L, TipoGasto.GASOLINA, BigDecimal.TEN, BigDecimal.ONE, null,
				HOY, null, HOY, AHORA)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("kilometraje");
	}

	@Test
	void otroExigeDescripcion() {
		assertThatThrownBy(
				() -> GastoVehiculo.registrar(7L, TipoGasto.OTRO, BigDecimal.TEN, null, null, HOY, " ", HOY, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("descripción");
	}

	@Test
	void elMontoDebeSerMayorACero() {
		assertThatThrownBy(
				() -> GastoVehiculo.registrar(7L, TipoGasto.LAVADO, BigDecimal.ZERO, null, null, HOY, null, HOY, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("monto");
	}

	@Test
	void laFechaNoPuedeSerFutura() {
		assertThatThrownBy(() -> GastoVehiculo.registrar(7L, TipoGasto.CASETAS, BigDecimal.TEN, null, null,
				HOY.plusDays(1), null, HOY, AHORA)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("fecha");
	}

	@Test
	void laDescripcionNoPasaDe200Caracteres() {
		assertThatThrownBy(() -> GastoVehiculo.registrar(7L, TipoGasto.OTRO, BigDecimal.TEN, null, null, HOY,
				"x".repeat(201), HOY, AHORA)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("200");
	}

	@Test
	void reemplazarCambiaTodosLosDatosYDejaNuloLoQueNoAplica() {
		GastoVehiculo gasto = GastoVehiculo.registrar(7L, TipoGasto.GASOLINA, BigDecimal.TEN, BigDecimal.ONE, 100, HOY,
				null, HOY, AHORA);

		gasto.reemplazar(TipoGasto.ESTACIONAMIENTO, new BigDecimal("40"), new BigDecimal("3"), null, HOY.minusDays(1),
				"  Plaza  ", HOY, AHORA);

		assertThat(gasto.getTipo()).isEqualTo(TipoGasto.ESTACIONAMIENTO);
		assertThat(gasto.getLitros()).isNull();
		assertThat(gasto.getDescripcion()).isEqualTo("Plaza");
		assertThat(gasto.getFecha()).isEqualTo(HOY.minusDays(1));
	}
}
