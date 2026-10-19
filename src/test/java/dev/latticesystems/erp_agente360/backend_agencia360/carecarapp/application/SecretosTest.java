package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThatCode;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ReglasContrasena;
import org.junit.jupiter.api.RepeatedTest;

class SecretosTest {

	@RepeatedTest(50)
	void laContrasenaTemporalCumpleLasReglas() {
		assertThatCode(() -> ReglasContrasena.validar(Secretos.nuevaContrasenaTemporal())).doesNotThrowAnyException();
	}
}
