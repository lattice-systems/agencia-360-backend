package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CuentaAppTest {

	private static final Instant AHORA = Instant.parse("2026-10-19T16:00:00Z");

	@Test
	void abreLaCuentaActivaYConContrasenaTemporal() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "hash");

		assertThat(cuenta.isActiva()).isTrue();
		assertThat(cuenta.isDebeCambiarContrasena()).isTrue();
		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();
	}

	@Test
	void seBloqueaQuinceMinutosAlQuintoIntentoFallido() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "hash");

		for (int i = 0; i < 4; i++) {
			cuenta.registrarIntentoFallido(AHORA);
		}
		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();

		cuenta.registrarIntentoFallido(AHORA);

		assertThat(cuenta.estaBloqueada(AHORA.plusSeconds(60))).isTrue();
		assertThat(cuenta.segundosDeBloqueo(AHORA.plusSeconds(60))).isEqualTo(14 * 60);
		assertThat(cuenta.estaBloqueada(AHORA.plusSeconds(15 * 60))).isFalse();
	}

	@Test
	void unIngresoCorrectoReiniciaLosIntentos() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "hash");
		for (int i = 0; i < 4; i++) {
			cuenta.registrarIntentoFallido(AHORA);
		}

		cuenta.registrarIngreso();
		cuenta.registrarIntentoFallido(AHORA);

		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();
	}

	@Test
	void cambiarLaContrasenaYaNoLaDejaTemporal() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "hash");

		cuenta.cambiarContrasena("otro-hash");

		assertThat(cuenta.isDebeCambiarContrasena()).isFalse();
		assertThat(cuenta.getContrasenaHash()).isEqualTo("otro-hash");
	}

	@Test
	void unCodigoDeRecuperacionVenceSeUsaUnaVezYAdmiteCincoIntentos() {
		CodigoRecuperacion codigo = CodigoRecuperacion.emitir(1L, "hash", AHORA);

		assertThat(codigo.estaVigente(AHORA.plusSeconds(14 * 60))).isTrue();
		assertThat(codigo.estaVigente(AHORA.plusSeconds(15 * 60))).isFalse();

		for (int i = 0; i < 5; i++) {
			codigo.registrarIntentoFallido();
		}
		assertThat(codigo.estaVigente(AHORA)).isFalse();

		CodigoRecuperacion otro = CodigoRecuperacion.emitir(1L, "hash", AHORA);
		otro.cerrar(AHORA);
		assertThat(otro.estaVigente(AHORA)).isFalse();
	}
}
