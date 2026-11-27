package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Timbrador.Timbre;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante.Concepto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El PDF de la factura simulada de un pago de la app (V2-M08.06). */
class FacturaAppPdfTest {

	@Test
	void traeLosDatosDeLaFacturaYDiceQueEsSimulada() throws Exception {
		Comprobante comprobante = new Comprobante("A", 124, LocalDateTime.of(2026, 11, 26, 12, 0),
				new Comprobante.Emisor("EKU9003173C9", "ESCUELA KEMPER URGATE", "601", "42501"),
				new Comprobante.Receptor("GODE561231GR8", "DOLORES GÓMEZ", "37000", "612", "G03"),
				List.of(Concepto.manoDeObra("Afinación", 1, new BigDecimal("1200.00")),
						Concepto.refaccion("Filtro de aceite", 3, new BigDecimal("450.00"))),
				Comprobante.FORMA_DEBITO);
		UUID uuid = UUID.fromString("6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");
		Timbre timbre = new Timbre(uuid, Instant.parse("2026-11-26T18:00:05Z"), "SIM010101AA1",
				"<cfdi:Comprobante/>".getBytes(StandardCharsets.UTF_8));

		byte[] pdf = new FacturaAppPdf().armar(comprobante, timbre);

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		PdfReader lector = new PdfReader(pdf);
		String texto = new PdfTextExtractor(lector).getTextFromPage(1).replaceAll("\\s+", " ");
		lector.close();
		assertThat(texto).contains("FACTURA SIMULADA, SIN VALIDEZ FISCAL", "Factura simulada, sin validez fiscal",
				"Folio A-000124", "26/11/2026 12:00", uuid.toString(), "EKU9003173C9", "ESCUELA KEMPER URGATE",
				"GODE561231GR8", "DOLORES GÓMEZ", "Uso del CFDI: G03", "Afinación", "Filtro de aceite", "78181500",
				"$1,422.41", "$227.59", "$1,650.00", "28 · Tarjeta de débito", "PUE", "26/11/2026 12:00:05");
	}
}
