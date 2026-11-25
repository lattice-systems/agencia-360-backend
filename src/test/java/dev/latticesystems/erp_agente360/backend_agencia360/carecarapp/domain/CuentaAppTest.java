package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CuentaAppTest {

	private static final Instant AHORA = Instant.parse("2026-10-19T16:00:00Z");

	@Test
	void abreLaCuentaActivaYConContrasenaTemporal() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");

		assertThat(cuenta.isActiva()).isTrue();
		assertThat(cuenta.isDebeCambiarContrasena()).isTrue();
		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();
	}

	@Test
	void seBloqueaQuinceMinutosAlQuintoIntentoFallido() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");

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
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");
		for (int i = 0; i < 4; i++) {
			cuenta.registrarIntentoFallido(AHORA);
		}

		cuenta.registrarIngreso();
		cuenta.registrarIntentoFallido(AHORA);

		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();
	}

	@Test
	void cambiarLaContrasenaYaNoLaDejaTemporal() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");

		cuenta.cambiarContrasena("otro-hash");

		assertThat(cuenta.isDebeCambiarContrasena()).isFalse();
		assertThat(cuenta.getContrasenaHash()).isEqualTo("otro-hash");
	}

	@Test
	void elUsuarioEsElCorreoSinEspaciosYEnMinusculas() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "  Ana.Lopez@Correo.com ", "hash");

		assertThat(cuenta.getUsuario()).isEqualTo("ana.lopez@correo.com");

		cuenta.cambiarUsuario("ANA@NUEVO.COM");
		assertThat(cuenta.getUsuario()).isEqualTo("ana@nuevo.com");
	}

	@Test
	void cancelarlaLaDesactivaLeQuitaElCorreoYLaDejaConOtraContrasena() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");
		ReflectionTestUtils.setField(cuenta, "id", 9L);
		for (int i = 0; i < 4; i++) {
			cuenta.registrarIntentoFallido(AHORA);
		}

		cuenta.cancelar("hash-de-un-secreto-que-nadie-guarda");

		assertThat(cuenta.isActiva()).isFalse();
		assertThat(cuenta.getUsuario()).isEqualTo("eliminada-9").doesNotContain("@");
		assertThat(cuenta.getContrasenaHash()).isEqualTo("hash-de-un-secreto-que-nadie-guarda");
		assertThat(cuenta.isDebeCambiarContrasena()).isFalse();
		assertThat(cuenta.fueCancelada()).isTrue();
		// Los intentos fallidos de antes no quedan colgando.
		cuenta.registrarIntentoFallido(AHORA);
		assertThat(cuenta.estaBloqueada(AHORA)).isFalse();
	}

	@Test
	void cancelarExigeElHashDeLaContrasenaNueva() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");

		assertThatThrownBy(() -> cuenta.cancelar(null)).isInstanceOf(NullPointerException.class);
		assertThat(cuenta.isActiva()).isTrue();
	}

	@Test
	void unaCuentaDesactivadaPorElPersonalNoCuentaComoCancelada() {
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");

		cuenta.desactivar();

		assertThat(cuenta.fueCancelada()).isFalse();
		assertThat(cuenta.getUsuario()).isEqualTo("ana@correo.com");
	}

	@Test
	void laCuentaSiempreTieneUsuario() {
		assertThatThrownBy(() -> CuentaApp.abrir(1L, " ", "hash")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El usuario de la cuenta es obligatorio");
		CuentaApp cuenta = CuentaApp.abrir(1L, "ana@correo.com", "hash");
		assertThatThrownBy(() -> cuenta.cambiarUsuario(null)).isInstanceOf(IllegalArgumentException.class);
		assertThat(cuenta.getUsuario()).isEqualTo("ana@correo.com");
	}
}
