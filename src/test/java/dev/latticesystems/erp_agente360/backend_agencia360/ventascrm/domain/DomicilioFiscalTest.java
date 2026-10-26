package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomicilioFiscalTest {

	@Test
	void guardaCadaParteSinEspaciosSobrantes() {
		DomicilioFiscal domicilio = DomicilioFiscal.de(" Guanajuato ", " León ", " Centro ",
				" Blvd. López Mateos 1102 ", " 37000 ");

		assertThat(domicilio.getEstado()).isEqualTo("Guanajuato");
		assertThat(domicilio.getCiudad()).isEqualTo("León");
		assertThat(domicilio.getColonia()).isEqualTo("Centro");
		assertThat(domicilio.getCalleNumero()).isEqualTo("Blvd. López Mateos 1102");
		assertThat(domicilio.getCodigoPostal()).isEqualTo("37000");
	}

	@Test
	void enLaFacturaVaEnUnaLinea() {
		assertThat(DatosFiscalesDePrueba.domicilioDeAna().enUnaLinea())
				.isEqualTo("Blvd. López Mateos 1102, Col. Centro, C.P. 37000, León, Guanajuato");
	}

	@Test
	void todasLasPartesSonObligatorias() {
		assertThatThrownBy(() -> DomicilioFiscal.de(" ", "León", "Centro", "Juárez 10", "37000"))
				.hasMessage("Captura el estado del domicilio fiscal");
		assertThatThrownBy(() -> DomicilioFiscal.de("Guanajuato", null, "Centro", "Juárez 10", "37000"))
				.hasMessage("Captura la ciudad del domicilio fiscal");
		assertThatThrownBy(() -> DomicilioFiscal.de("Guanajuato", "León", "", "Juárez 10", "37000"))
				.hasMessage("Captura la colonia del domicilio fiscal");
		assertThatThrownBy(() -> DomicilioFiscal.de("Guanajuato", "León", "Centro", null, "37000"))
				.hasMessage("Captura la calle y el número del domicilio fiscal");
		assertThatThrownBy(() -> DomicilioFiscal.de("Guanajuato", "León", "Centro", "Juárez 10", null))
				.hasMessage("Captura el código postal del domicilio fiscal");
	}

	@ParameterizedTest
	@ValueSource(strings = {"3700", "370000", "37A00"})
	void elCodigoPostalSonCincoDigitos(String codigoPostal) {
		assertThatThrownBy(() -> DomicilioFiscal.de("Guanajuato", "León", "Centro", "Juárez 10", codigoPostal))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void limitaElLargoParaQueQuepaEnLaFactura() {
		assertThatThrownBy(() -> DomicilioFiscal.de("Guanajuato", "León", "Centro", "C".repeat(91), "37000"))
				.hasMessage("En el domicilio fiscal, la calle y el número admite hasta 90 caracteres");
		String largo = DomicilioFiscal.de("E".repeat(40), "C".repeat(60), "O".repeat(80), "N".repeat(90), "37000")
				.enUnaLinea();
		assertThat(largo).hasSizeLessThanOrEqualTo(300);
	}
}
