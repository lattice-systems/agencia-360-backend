package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ManualTest {

	@Test
	void elTituloLlevaLaMarcaElTipoYElModelo() {
		Manual manual = Manual.nuevo(" Nissan ", " March ", 2026);

		assertThat(manual.titulo()).isEqualTo("Nissan March 2026 Manual de propietario");
	}

	@Test
	void validaLosDatos() {
		assertThatThrownBy(() -> Manual.nuevo(" ", "March", 2026)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Manual.nuevo("Nissan", null, 2026)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Manual.nuevo("Nissan", "March", 1899)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El modelo debe ser 1900 o posterior");
	}

	@Test
	void elArchivoNoPuedeEstarVacioNiPasarDeVeinteMegas() {
		Manual manual = Manual.nuevo("Nissan", "March", 2026);

		assertThatThrownBy(() -> manual.ponerArchivo("a.pdf", 0, 1L)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> manual.ponerArchivo("a.pdf", Manual.TAMANO_MAXIMO + 1, 1L))
				.isInstanceOf(IllegalArgumentException.class);
		manual.ponerArchivo("a.pdf", Manual.TAMANO_MAXIMO, 1L);
		assertThat(manual.getTamanoBytes()).isEqualTo(Manual.TAMANO_MAXIMO);
	}
}
