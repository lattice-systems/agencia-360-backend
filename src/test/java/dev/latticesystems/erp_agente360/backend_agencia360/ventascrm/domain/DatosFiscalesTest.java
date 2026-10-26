package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatosFiscalesTest {

	private static final DomicilioFiscal DOMICILIO = DatosFiscalesDePrueba.domicilioDeAna();

	@Test
	void aceptaElRfcDePersonaFisicaYDePersonaMoral() {
		assertThat(DatosFiscales.de("LOHA850101AB1", "Ana López Hernández", DOMICILIO, "612").getRfc())
				.isEqualTo("LOHA850101AB1");
		assertThat(DatosFiscales.de("ABC010203XY9", "Autopartes del Bajío SA de CV", DOMICILIO, "601").getRfc())
				.isEqualTo("ABC010203XY9");
	}

	@Test
	void guardaElRfcEnMayusculasYSinEspaciosSobrantes() {
		DatosFiscales datos = DatosFiscales.de("  loha850101ab1 ", "  Ana López Hernández ", DOMICILIO, " 612 ");

		assertThat(datos.getRfc()).isEqualTo("LOHA850101AB1");
		assertThat(datos.getNombre()).isEqualTo("Ana López Hernández");
		assertThat(datos.getDomicilio()).isSameAs(DOMICILIO);
		assertThat(datos.getRegimen()).isEqualTo(RegimenFiscal.ACTIVIDADES_EMPRESARIALES_Y_PROFESIONALES);
	}

	@ParameterizedTest
	@ValueSource(strings = {"LOHA850101", "LOHA8501019AB1", "LO1A850101AB1", "LOHA851301AB1", "LOHA-850101-AB1"})
	void rechazaUnRfcConFormatoInvalido(String rfc) {
		assertThatThrownBy(() -> DatosFiscales.de(rfc, "Ana López", DOMICILIO, "612"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RFC");
	}

	@Test
	void todosLosDatosSonObligatorios() {
		assertThatThrownBy(() -> DatosFiscales.de(" ", "Ana López", DOMICILIO, "612"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RFC");
		assertThatThrownBy(() -> DatosFiscales.de("LOHA850101AB1", null, DOMICILIO, "612"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nombre");
		assertThatThrownBy(() -> DatosFiscales.de("LOHA850101AB1", "Ana López", null, "612"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("domicilio");
		assertThatThrownBy(() -> DatosFiscales.de("LOHA850101AB1", "Ana López", DOMICILIO, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("régimen");
	}

	@Test
	void rechazaUnRegimenQueNoEstaEnElCatalogoDelSat() {
		assertThatThrownBy(() -> DatosFiscales.de("LOHA850101AB1", "Ana López", DOMICILIO, "999"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("régimen");
	}

	@Test
	void limitaElLargoDelNombre() {
		assertThatThrownBy(() -> DatosFiscales.de("LOHA850101AB1", "A".repeat(201), DOMICILIO, "612"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("200");
	}

	@Test
	void elRegimenSeBuscaPorSuClaveDelSat() {
		assertThat(RegimenFiscal.desdeClave("626")).isEqualTo(RegimenFiscal.SIMPLIFICADO_DE_CONFIANZA);
		assertThat(RegimenFiscal.SIMPLIFICADO_DE_CONFIANZA.getClave()).isEqualTo("626");
		assertThat(RegimenFiscal.SIMPLIFICADO_DE_CONFIANZA.getDescripcion())
				.isEqualTo("Régimen Simplificado de Confianza");
	}
}
