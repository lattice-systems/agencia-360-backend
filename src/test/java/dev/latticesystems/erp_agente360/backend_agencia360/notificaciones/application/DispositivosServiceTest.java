package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2-M06.02: el registro del token valida lo que llega de la app antes de tocar
 * la base.
 */
class DispositivosServiceTest {

	private static final String INSTALACION = "5b0c1d52-3f4e-4a1b-9c6d-7e8f90a1b2c3";

	private Dispositivos dispositivos;
	private DispositivosService servicio;

	@BeforeEach
	void preparar() {
		dispositivos = mock(Dispositivos.class);
		servicio = new DispositivosService(dispositivos);
	}

	@Test
	void registraConElIdiomaPorDefectoYLaPlataformaEnMinusculas() {
		when(dispositivos.registrar(5L, INSTALACION, "token", "android", "es")).thenReturn(true);

		servicio.registrar(5L, INSTALACION, "token", "ANDROID", null);

		verify(dispositivos).registrar(5L, INSTALACION, "token", "android", "es");
	}

	@Test
	void guardaElIdiomaQueMandaLaApp() {
		when(dispositivos.registrar(5L, INSTALACION, "token", "ios", "en")).thenReturn(true);

		servicio.registrar(5L, INSTALACION, "token", "ios", "en");

		verify(dispositivos).registrar(5L, INSTALACION, "token", "ios", "en");
	}

	@Test
	void unClienteSinCuentaDeLaAppEsUn404() {
		when(dispositivos.registrar(any(), any(), any(), any(), any())).thenReturn(false);

		assertThatThrownBy(() -> servicio.registrar(5L, INSTALACION, "token", "ios", "es"))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void rechazaUnaPlataformaDesconocida() {
		assertThatThrownBy(() -> servicio.registrar(5L, INSTALACION, "token", "windows", "es"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plataforma");
		verifyNoInteractions(dispositivos);
	}

	@Test
	void rechazaUnaInstalacionConCaracteresRaros() {
		assertThatThrownBy(() -> servicio.registrar(5L, "../../etc/passwd", "token", "ios", "es"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("instalación");
		assertThatThrownBy(() -> servicio.darDeBaja(5L, "corta")).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(dispositivos);
	}

	@Test
	void darDeBajaEsIdempotente() {
		assertThatCode(() -> servicio.darDeBaja(5L, INSTALACION)).doesNotThrowAnyException();

		verify(dispositivos).darDeBaja(5L, INSTALACION);
	}
}
