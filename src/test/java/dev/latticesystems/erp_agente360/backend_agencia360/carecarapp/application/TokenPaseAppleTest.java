package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenPaseAppleTest {

	private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres";

	@Test
	void elTokenEsEstableParaLaMismaCita() {
		TokenPaseApple token = new TokenPaseApple(SECRETO);

		assertThat(token.de(42L)).isEqualTo(token.de(42L)).hasSizeGreaterThanOrEqualTo(16);
	}

	@Test
	void cadaCitaTieneSuPropioToken() {
		TokenPaseApple token = new TokenPaseApple(SECRETO);

		assertThat(token.de(42L)).isNotEqualTo(token.de(43L));
	}

	@Test
	void otroSecretoDaOtroToken() {
		assertThat(new TokenPaseApple(SECRETO).de(42L)).isNotEqualTo(new TokenPaseApple(SECRETO + "-distinto").de(42L));
	}

	@Test
	void validaSoloElTokenDeEsaCita() {
		TokenPaseApple token = new TokenPaseApple(SECRETO);

		assertThat(token.valido(42L, token.de(42L))).isTrue();
		assertThat(token.valido(43L, token.de(42L))).isFalse();
		assertThat(token.valido(42L, "inventado")).isFalse();
		assertThat(token.valido(42L, null)).isFalse();
	}

	@Test
	void noEsElMismoValorQueLaFirmaDelQr() {
		assertThat(new TokenPaseApple(SECRETO).de(42L)).isNotEqualTo(new FirmaCitas(SECRETO).contenidoQr(42L));
	}

	@Test
	void rechazaUnSecretoCorto() {
		assertThatThrownBy(() -> new TokenPaseApple("corto")).isInstanceOf(IllegalStateException.class);
	}
}
