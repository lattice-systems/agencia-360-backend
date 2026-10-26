package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MontosTest {

	@Test
	void elIvaVieneIncluidoEnElTotal() {
		Montos montos = Montos.desglosar(new BigDecimal("116"));

		assertThat(montos.subtotal()).isEqualByComparingTo("100.00");
		assertThat(montos.iva()).isEqualByComparingTo("16.00");
		assertThat(montos.total()).isEqualByComparingTo("116.00");
	}

	@Test
	void laSumaSiempreDaElTotalAunqueRedondee() {
		Montos montos = Montos.desglosar(new BigDecimal("100.00"));

		assertThat(montos.subtotal()).isEqualByComparingTo("86.21");
		assertThat(montos.iva()).isEqualByComparingTo("13.79");
		assertThat(montos.subtotal().add(montos.iva())).isEqualByComparingTo("100.00");
	}

	@Test
	void rechazaUnTotalNegativo() {
		assertThatThrownBy(() -> Montos.desglosar(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
	}
}
