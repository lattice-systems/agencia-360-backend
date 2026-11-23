package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenRecuperacionTest {

	private static final Instant AHORA = Instant.parse("2026-10-19T16:00:00Z");

	@Test
	void venceALosQuinceMinutos() {
		TokenRecuperacion token = TokenRecuperacion.emitir(1L, "hash-del-token", "hash-del-codigo", AHORA);

		assertThat(token.getExpira()).isEqualTo(AHORA.plusSeconds(15 * 60));
		assertThat(token.estaVigente(AHORA.plusSeconds(14 * 60))).isTrue();
		assertThat(token.estaVigente(AHORA.plusSeconds(15 * 60))).isFalse();
	}

	@Test
	void sirveUnaSolaVez() {
		TokenRecuperacion token = TokenRecuperacion.emitir(1L, "hash-del-token", "hash-del-codigo", AHORA);

		token.cerrar(AHORA);
		token.cerrar(AHORA.plusSeconds(60));

		assertThat(token.isUsado()).isTrue();
		assertThat(token.estaVigente(AHORA)).isFalse();
	}

	@Test
	void admiteCincoIntentosDeCodigo() {
		TokenRecuperacion token = TokenRecuperacion.emitir(1L, "hash-del-token", "hash-del-codigo", AHORA);

		for (int i = 0; i < 4; i++) {
			token.registrarIntentoFallido();
		}
		assertThat(token.estaVigente(AHORA)).isTrue();

		token.registrarIntentoFallido();
		assertThat(token.estaVigente(AHORA)).isFalse();
		assertThat(token.isUsado()).isFalse();
	}
}
