package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FirmaManualesTest {

	private static final String SECRETO = "secreto-de-prueba-0123456789-0123456789";
	private static final Instant AHORA = Instant.parse("2026-10-26T18:00:00Z");

	private static FirmaManuales firmaA(Instant instante) {
		return new FirmaManuales(SECRETO, Clock.fixed(instante, ZoneOffset.UTC));
	}

	@Test
	void laUrlFirmadaValeQuinceMinutos() {
		FirmaManuales.UrlFirmada firmada = firmaA(AHORA).firmar(5L);

		assertThat(firmada.expira()).isEqualTo(AHORA.plusSeconds(15 * 60).getEpochSecond());
		assertThat(firmaA(AHORA.plusSeconds(14 * 60)).esValida(5L, firmada.expira(), firmada.firma())).isTrue();
		assertThat(firmaA(AHORA.plusSeconds(16 * 60)).esValida(5L, firmada.expira(), firmada.firma())).isFalse();
	}

	@Test
	void laFirmaNoSirveParaOtroManualNiOtroVencimiento() {
		FirmaManuales firma = firmaA(AHORA);
		FirmaManuales.UrlFirmada firmada = firma.firmar(5L);

		assertThat(firma.esValida(6L, firmada.expira(), firmada.firma())).isFalse();
		assertThat(firma.esValida(5L, firmada.expira() + 3600, firmada.firma())).isFalse();
		assertThat(firma.esValida(5L, firmada.expira(), null)).isFalse();
	}

	@Test
	void otroSecretoNoReconoceLaFirma() {
		FirmaManuales.UrlFirmada firmada = firmaA(AHORA).firmar(5L);
		FirmaManuales otra = new FirmaManuales("otro-secreto-0123456789-0123456789-01",
				Clock.fixed(AHORA, ZoneOffset.UTC));

		assertThat(otra.esValida(5L, firmada.expira(), firmada.firma())).isFalse();
	}

	@Test
	void exigeUnSecretoLargo() {
		assertThatThrownBy(() -> new FirmaManuales("corto", Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class);
	}
}
