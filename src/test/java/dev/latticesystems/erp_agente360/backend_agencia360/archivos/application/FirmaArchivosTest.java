package dev.latticesystems.erp_agente360.backend_agencia360.archivos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FirmaArchivosTest {

	private static final String SECRETO = "secreto-de-prueba-0123456789-0123456789";
	private static final Instant AHORA = Instant.parse("2026-11-23T18:00:00Z");
	private static final String CLAVE = "evidencias/0f8c2b6e-2f6d-4c1e-9c2a-1b2c3d4e5f60";

	private static FirmaArchivos firmaA(Instant momento) {
		return new FirmaArchivos(SECRETO, Clock.fixed(momento, ZoneOffset.UTC));
	}

	@Test
	void unaUrlFirmadaSirveHastaQueVence() {
		FirmaArchivos.UrlFirmada firmada = firmaA(AHORA).firmar(CLAVE);

		assertThat(firmada.expira()).isEqualTo(AHORA.plus(FirmaArchivos.VIGENCIA).getEpochSecond());
		assertThat(firmaA(AHORA.plusSeconds(60)).esValida(CLAVE, firmada.expira(), firmada.firma())).isTrue();
		assertThat(firmaA(AHORA.plusSeconds(16 * 60)).esValida(CLAVE, firmada.expira(), firmada.firma())).isFalse();
	}

	@Test
	void laFirmaSoloSirveParaSuArchivoYSuVencimiento() {
		FirmaArchivos firma = firmaA(AHORA);
		FirmaArchivos.UrlFirmada firmada = firma.firmar(CLAVE);

		assertThat(firma.esValida("evidencias/otro", firmada.expira(), firmada.firma())).isFalse();
		assertThat(firma.esValida(CLAVE, firmada.expira() + 60, firmada.firma())).isFalse();
		assertThat(firma.esValida(CLAVE, firmada.expira(), null)).isFalse();
		assertThat(new FirmaArchivos("otro-secreto-de-prueba-0123456789-0123", Clock.fixed(AHORA, ZoneOffset.UTC))
				.esValida(CLAVE, firmada.expira(), firmada.firma())).isFalse();
	}

	@Test
	void exigeUnSecretoLargo() {
		assertThatThrownBy(() -> new FirmaArchivos("corto", Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class);
	}
}
