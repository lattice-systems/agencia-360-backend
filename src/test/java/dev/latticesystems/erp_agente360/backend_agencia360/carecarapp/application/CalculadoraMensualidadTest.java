package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalculadoraMensualidadTest {

	@Test
	void calculaElPagoFijoConLaTasaMensual() {
		// 300 000 - 60 000 = 240 000 a 48 meses con 14.99 % anual.
		var c = CalculadoraMensualidad.calcular(new BigDecimal("300000"), new BigDecimal("60000"), 48,
				new BigDecimal("0.1499"));

		assertThat(c.montoFinanciado()).isEqualByComparingTo("240000.00");
		assertThat(c.mensualidad()).isEqualByComparingTo("6678.16");
		assertThat(c.leyenda()).contains("no vinculante");
	}

	@Test
	void conTodoDeEngancheNoHayMensualidad() {
		var c = CalculadoraMensualidad.calcular(new BigDecimal("100"), new BigDecimal("100"), 12,
				new BigDecimal("0.1499"));
		assertThat(c.mensualidad()).isEqualByComparingTo("0");
	}
}
