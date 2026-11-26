package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposInvalidosException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RFC, catálogos y combinaciones de CFDI 4.0 de un perfil fiscal (V2-M08.05).
 */
class DatosFiscalesTest {

	@ParameterizedTest
	@ValueSource(strings = {"EKU9003173C9", "GODE561231GR8", "MUÑO900516NL7", "A&B0101018UA", "PEGJ800229AB2"})
	void rfcConFormaFechaYDigitoDelSat(String rfc) {
		assertThat(Rfc.problema(rfc)).isEmpty();
	}

	@Test
	void elLargoDiceElTipoDePersona() {
		assertThat(Rfc.tipoDe("EKU9003173C9")).contains(TipoPersona.MORAL);
		assertThat(Rfc.tipoDe(" gode561231gr8 ")).contains(TipoPersona.FISICA);
		assertThat(Rfc.tipoDe("ABC")).isEmpty();
	}

	@Test
	void rfcQueNoSirveDicePorQue() {
		assertThat(Rfc.problema("")).contains("Escribe el RFC");
		assertThat(Rfc.problema("ABC123").orElseThrow()).contains("12 caracteres");
		assertThat(Rfc.problema("1234567890123")).contains("El RFC no tiene la forma del SAT");
		assertThat(Rfc.problema("XAXX010101000")).contains("El RFC genérico no se usa en un perfil fiscal");
		assertThat(Rfc.problema("PEGJ810229AB5")).contains("La fecha del RFC no existe");
		assertThat(Rfc.problema("GODE561331GR8")).contains("La fecha del RFC no existe");
		assertThat(Rfc.problema("GODE561231GR9").orElseThrow()).contains("dígito verificador");
	}

	@Test
	void personaFisicaSeLimpiaYQuedaEnMayusculas() {
		DatosFiscales datos = DatosFiscales.validar(" gode561231gr8 ", "  Dolores   Gómez  ", "612", " 76000 ", "g03",
				" Lola@Correo.MX ");

		assertThat(datos.rfc()).isEqualTo("GODE561231GR8");
		assertThat(datos.tipo()).isEqualTo(TipoPersona.FISICA);
		assertThat(datos.razonSocial()).isEqualTo("DOLORES GÓMEZ");
		assertThat(datos.codigoPostal()).isEqualTo("76000");
		assertThat(datos.usoCfdi()).isEqualTo("G03");
		assertThat(datos.correo()).isEqualTo("lola@correo.mx");
	}

	@Test
	void personaMoralSinCorreo() {
		DatosFiscales datos = DatosFiscales.validar("EKU9003173C9", "Escuela Kemper Urgate", "601", "42501", "G03",
				"  ");

		assertThat(datos.tipo()).isEqualTo(TipoPersona.MORAL);
		assertThat(datos.correo()).isNull();
	}

	@Test
	void juntaTodosLosCamposQueFallan() {
		assertThatThrownBy(() -> DatosFiscales.validar("GODE561231GR9", " ", "999", "7600", "ZZZ", "sin-arroba"))
				.isInstanceOfSatisfying(CamposInvalidosException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo("DATOS_FISCALES_INVALIDOS");
					assertThat(e.getCampos()).containsOnlyKeys("rfc", "razonSocial", "regimenFiscal", "codigoPostal",
							"usoCfdi", "correo");
				});
	}

	@Test
	void elRegimenYElUsoDebenAplicarAlTipoDePersona() {
		assertThat(campos("EKU9003173C9", "612", "G03")).containsEntry("regimenFiscal",
				"Ese régimen no aplica a una persona moral");
		assertThat(campos("GODE561231GR8", "601", "G03")).containsEntry("regimenFiscal",
				"Ese régimen no aplica a una persona física");
		assertThat(campos("EKU9003173C9", "601", "D01")).containsEntry("usoCfdi",
				"Ese uso de CFDI no aplica a una persona moral");
	}

	@Test
	void elUsoDebeAceptarElRegimen() {
		assertThat(campos("GODE561231GR8", "605", "G03")).containsEntry("usoCfdi",
				"Ese uso de CFDI no se puede con el régimen 605");
		assertThat(campos("GODE561231GR8", "616", "G03")).containsKey("usoCfdi");
		assertThat(DatosFiscales.validar("GODE561231GR8", "Dolores", "605", "76000", "D01", null).usoCfdi())
				.isEqualTo("D01");
		assertThat(DatosFiscales.validar("GODE561231GR8", "Dolores", "616", "76000", "S01", null).usoCfdi())
				.isEqualTo("S01");
	}

	@Test
	void pagosYNominaNoSonUsosParaFacturarUnServicio() {
		assertThat(campos("GODE561231GR8", "612", "CP01")).containsEntry("usoCfdi",
				"Elige un uso de CFDI del catálogo del SAT");
		assertThat(campos("GODE561231GR8", "605", "CN01")).containsKey("usoCfdi");
	}

	@Test
	void cadaUsoAceptaSoloRegimenesDelCatalogo() {
		Set<String> regimenes = CatalogoFiscal.regimenes().stream().map(CatalogoFiscal.RegimenFiscal::clave)
				.collect(Collectors.toSet());
		for (CatalogoFiscal.UsoCfdi uso : CatalogoFiscal.usos()) {
			assertThat(regimenes).as(uso.clave()).containsAll(uso.regimenes());
		}
		assertThat(CatalogoFiscal.uso("S01").orElseThrow().regimenes()).containsExactlyInAnyOrderElementsOf(regimenes);
	}

	@Test
	void cadaRegimenTieneAlMenosUnUsoParaSuPersona() {
		for (CatalogoFiscal.RegimenFiscal regimen : CatalogoFiscal.regimenes()) {
			for (TipoPersona persona : TipoPersona.values()) {
				if (regimen.aplicaA(persona)) {
					assertThat(CatalogoFiscal.usos()).as(regimen.clave() + " " + persona)
							.anyMatch(uso -> uso.aplicaA(persona) && uso.aceptaRegimen(regimen.clave()));
				}
			}
		}
	}

	private static Map<String, String> campos(String rfc, String regimen, String uso) {
		try {
			DatosFiscales.validar(rfc, "Nombre", regimen, "76000", uso, null);
		} catch (CamposInvalidosException e) {
			return e.getCampos();
		}
		return Map.of();
	}
}
