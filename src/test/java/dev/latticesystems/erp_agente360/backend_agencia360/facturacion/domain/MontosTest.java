package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MontosTest {

	@Test
	void desglosaElIvaDelTotalYLaSumaDaElTotal() {
		Montos montos = Montos.desglosar(new BigDecimal("489900.00"));

		assertThat(montos.subtotal()).isEqualByComparingTo("422327.59");
		assertThat(montos.iva()).isEqualByComparingTo("67572.41");
		assertThat(montos.total()).isEqualByComparingTo("489900.00");
		assertThat(montos.subtotal().add(montos.iva())).isEqualByComparingTo(montos.total());
	}

	@Test
	void unTotalRedondoTambienCuadra() {
		Montos montos = Montos.desglosar(new BigDecimal("116"));

		assertThat(montos.subtotal()).isEqualByComparingTo("100.00");
		assertThat(montos.iva()).isEqualByComparingTo("16.00");
		assertThat(montos.total().scale()).isEqualTo(2);
	}

	@Test
	void elTotalDebeSerMayorACero() {
		assertThatThrownBy(() -> Montos.desglosar(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El total de la factura debe ser mayor a cero");
		assertThatThrownBy(() -> Montos.desglosar(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
	}
}
