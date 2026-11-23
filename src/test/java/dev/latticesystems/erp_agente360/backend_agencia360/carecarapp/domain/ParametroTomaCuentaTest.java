package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Validaciones del parámetro de toma a cuenta (V2-M03.06). */
class ParametroTomaCuentaTest {

	private static ParametroTomaCuenta con(String marca, String modelo, int anio, String valor, String depreciacion,
			String factorKm, String factorEstado) {
		return ParametroTomaCuenta.nuevo(marca, modelo, anio, new BigDecimal(valor), new BigDecimal(depreciacion),
				new BigDecimal(factorKm), new BigDecimal(factorEstado));
	}

	@Test
	void naceActivoYConLosDatosLimpios() {
		ParametroTomaCuenta parametro = con(" Nissan ", "Versa", 2021, "250000", "0.1", "0.05", "0.1");

		assertThat(parametro.isActivo()).isTrue();
		assertThat(parametro.getMarca()).isEqualTo("Nissan");
		assertThat(parametro.getValorBase()).isEqualByComparingTo("250000");
	}

	@Test
	void aceptaLosLimitesDeCadaFactor() {
		ParametroTomaCuenta bajos = con("Nissan", "Versa", 2021, "0", "0", "0", "0");
		ParametroTomaCuenta altos = con("Nissan", "Versa", 2021, "1", "0.5", "0.2", "0.3");

		assertThat(bajos.getValorBase()).isEqualByComparingTo("0");
		assertThat(altos.getFactorEstado()).isEqualByComparingTo("0.3");
	}

	@Test
	void rechazaDatosFueraDeRango() {
		assertThatThrownBy(() -> con(" ", "Versa", 2021, "1", "0.1", "0.05", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "", 2021, "1", "0.1", "0.05", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "Versa", 1899, "1", "0.1", "0.05", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "Versa", 2021, "-1", "0.1", "0.05", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "Versa", 2021, "1", "0.51", "0.05", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "Versa", 2021, "1", "0.1", "0.21", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "Versa", 2021, "1", "0.1", "0.05", "0.31"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> con("Nissan", "Versa", 2021, "1", "-0.1", "0.05", "0.1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void desactivarLoDejaSinUsoSinBorrarlo() {
		ParametroTomaCuenta parametro = con("Nissan", "Versa", 2021, "1", "0.1", "0.05", "0.1");

		parametro.desactivar();

		assertThat(parametro.isActivo()).isFalse();
	}
}
