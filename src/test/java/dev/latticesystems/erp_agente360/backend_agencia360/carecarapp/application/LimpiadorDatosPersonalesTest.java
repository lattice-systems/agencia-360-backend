package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LimpiadorDatosPersonalesTest {

	@Test
	void quitaCorreoTelefonoVinYPlacas() {
		String limpio = LimpiadorDatosPersonales.limpiar("Soy ana@correo.com, tel 477 123 4567, VIN 3N1CK3CD5RL000001 "
				+ "y placas GTO-776-V. ¿Cada cuánto cambio el aceite?");

		assertThat(limpio).doesNotContain("ana@correo.com", "477 123 4567", "3N1CK3CD5RL000001", "GTO-776-V")
				.contains("¿Cada cuánto cambio el aceite?");
	}

	@Test
	void dejaIgualUnaPreguntaSinDatosPersonales() {
		String pregunta = "¿Qué presión llevan las llantas a los 10 000 km?";

		assertThat(LimpiadorDatosPersonales.limpiar(pregunta)).isEqualTo(pregunta);
	}
}
