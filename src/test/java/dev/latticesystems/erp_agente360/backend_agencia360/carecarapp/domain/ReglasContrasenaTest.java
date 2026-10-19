package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReglasContrasenaTest {

	@Test
	void aceptaDe8A72ConLetraYNumero() {
		assertThatCode(() -> ReglasContrasena.validar("Carecar1")).doesNotThrowAnyException();
		assertThatCode(() -> ReglasContrasena.validar("a1".repeat(36))).doesNotThrowAnyException();
	}

	@Test
	void rechazaLasMuyCortasOMuyLargas() {
		assertThatThrownBy(() -> ReglasContrasena.validar("abc1234")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La contraseña debe tener de 8 a 72 caracteres");
		assertThatThrownBy(() -> ReglasContrasena.validar("a1".repeat(36) + "x"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void exigeAlMenosUnaLetraYUnNumero() {
		assertThatThrownBy(() -> ReglasContrasena.validar("solamenteletras"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La contraseña debe tener al menos una letra y un número");
		assertThatThrownBy(() -> ReglasContrasena.validar("12345678")).isInstanceOf(IllegalArgumentException.class);
	}
}
