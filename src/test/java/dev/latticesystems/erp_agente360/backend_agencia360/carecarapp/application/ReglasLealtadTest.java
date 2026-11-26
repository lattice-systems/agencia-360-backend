package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.NivelLealtad;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReglasLealtadTest {

	private final ReglasLealtad reglas = ReglasLealtad.porOmision();

	@Test
	void unPuntoPorCadaDiezPesosTruncado() {
		assertThat(reglas.puntosPorServicio(new BigDecimal("1650.00"))).isEqualTo(165);
		assertThat(reglas.puntosPorServicio(new BigDecimal("1659.99"))).isEqualTo(165);
		assertThat(reglas.puntosPorServicio(new BigDecimal("9.99"))).isZero();
		assertThat(reglas.puntosPorServicio(BigDecimal.ZERO)).isZero();
		assertThat(reglas.puntosPorServicio(null)).isZero();
	}

	@Test
	void nivelPorPuntosGanadosConPlataDesde1500YOroDesde4000() {
		assertThat(reglas.nivel(0)).isEqualTo(NivelLealtad.BRONCE);
		assertThat(reglas.nivel(1499)).isEqualTo(NivelLealtad.BRONCE);
		assertThat(reglas.nivel(1500)).isEqualTo(NivelLealtad.PLATA);
		assertThat(reglas.nivel(3999)).isEqualTo(NivelLealtad.PLATA);
		assertThat(reglas.nivel(4000)).isEqualTo(NivelLealtad.ORO);
		assertThat(reglas.siguiente(NivelLealtad.BRONCE)).isEqualTo(NivelLealtad.PLATA);
		assertThat(reglas.siguiente(NivelLealtad.PLATA)).isEqualTo(NivelLealtad.ORO);
		assertThat(reglas.siguiente(NivelLealtad.ORO)).isNull();
		assertThat(reglas.umbral(NivelLealtad.ORO)).isEqualTo(4000);
	}

	@Test
	void lasDemasReglasYLasInvalidas() {
		assertThat(reglas.vigenciaCanje()).isEqualTo(Duration.ofDays(30));
		assertThat(reglas.puntosPorReferido()).isEqualTo(500);
		assertThat(reglas.referidosAbiertos()).isEqualTo(10);
		assertThatThrownBy(() -> new ReglasLealtad(10, 4000, 1500, 30, 500, 10))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReglasLealtad(0, 1500, 4000, 30, 500, 10))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
