package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FirmaCitasTest {

	private static final String SECRETO = "test-only-citas-qr-0123456789-0123456789";

	@Test
	void elContenidoDelQrLlevaElIdYUnaFirmaQueSeVerifica() {
		FirmaCitas firma = new FirmaCitas(SECRETO);
		String contenido = firma.contenidoQr(57L);
		assertThat(contenido).startsWith("CARECAR-CITA:57.");
		assertThat(firma.citaDe(contenido)).contains(57L);
	}

	@Test
	void unaFirmaAlteradaOdeOtraCitaOdeOtraLlaveNoSeAcepta() {
		FirmaCitas firma = new FirmaCitas(SECRETO);
		String contenido = firma.contenidoQr(57L);
		String firmaDeLaCita = contenido.substring(contenido.indexOf('.') + 1);
		assertThat(firma.citaDe("CARECAR-CITA:58." + firmaDeLaCita)).isEmpty();
		assertThat(firma.citaDe(contenido + "x")).isEmpty();
		assertThat(firma.citaDe("CARECAR-CITA:57.")).isEmpty();
		assertThat(firma.citaDe("otra-cosa")).isEmpty();
		assertThat(firma.citaDe(null)).isEmpty();
		assertThat(new FirmaCitas("otra-llave-0123456789-0123456789-0123456789").citaDe(contenido)).isEmpty();
	}

	@Test
	void exigeUnaLlaveDeAlMenos32Caracteres() {
		assertThatThrownBy(() -> new FirmaCitas("corta")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new FirmaCitas(null)).isInstanceOf(IllegalStateException.class);
	}
}
