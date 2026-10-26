package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura.Parte;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura.Unidad;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.Montos;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FacturaPdfTest {

	@Test
	void elPdfLlevaElFolioElEmisorElReceptorLaUnidadYLosMontos() throws IOException {
		DatosFactura datos = new DatosFactura("F-000007", 4L, 2L, Instant.parse("2026-10-24T22:30:00Z"),
				new Parte("AGE2401015K3", "Agencia360 Automotriz S.A. de C.V.", "Blvd. López Mateos 1234, León",
						"601 - General de Ley Personas Morales"),
				new Parte("HELL800101AB1", "Luis Herrera López", "Calle Hidalgo 10, León", "612 - Personas Físicas"),
				new Unidad(3L, "3N1AB8CV5PY123456", "Nissan", "Sentra", "Advance", 2026, "Blanco"),
				Montos.desglosar(new BigDecimal("489900.00")));

		byte[] pdf = new FacturaPdf().armar(datos);

		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		PdfReader lector = new PdfReader(pdf);
		String texto = new PdfTextExtractor(lector).getTextFromPage(1);
		lector.close();
		assertThat(texto).contains("Folio F-000007", "24/10/2026 16:30", "AGE2401015K3", "Agencia360 Automotriz",
				"HELL800101AB1", "Luis Herrera López", "612 - Personas Físicas", "Nissan Sentra Advance",
				"3N1AB8CV5PY123456", "Modelo: 2026", "$422,327.59", "$67,572.41", "$489,900.00",
				"Folio interno, sin timbrado");
	}
}
