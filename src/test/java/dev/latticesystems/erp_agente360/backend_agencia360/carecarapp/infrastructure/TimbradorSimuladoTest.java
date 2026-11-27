package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Timbrador.Timbre;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante.Concepto;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposNoProcesablesException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * El timbrado simulado (V2-M08.06, ADR 0009): el XML con la estructura de CFDI
 * 4.0, el timbre simulado y los rechazos como los de un PAC.
 */
class TimbradorSimuladoTest {

	private static final Comprobante.Emisor EMISOR = new Comprobante.Emisor("EKU9003173C9", "ESCUELA KEMPER URGATE",
			"601", "42501");
	private static final Comprobante.Receptor FISICA = new Comprobante.Receptor("GODE561231GR8", "DOLORES GÓMEZ",
			"37000", "612", "G03");

	private final TimbradorSimulado timbrador = new TimbradorSimulado(
			Clock.fixed(Instant.parse("2026-11-26T18:00:00Z"), ZoneOffset.UTC), ZoneId.of("America/Mexico_City"));

	@Test
	void armaElCfdiConEmisorReceptorConceptosEIva() throws Exception {
		Timbre timbre = timbrador.timbrar(comprobante(FISICA, Comprobante.FORMA_DEBITO));

		Document xml = leer(timbre.xml());
		Element raiz = xml.getDocumentElement();
		assertThat(raiz.getNamespaceURI()).isEqualTo(TimbradorSimulado.CFDI);
		assertThat(raiz.getLocalName()).isEqualTo("Comprobante");
		assertThat(raiz.getAttribute("Version")).isEqualTo("4.0");
		assertThat(raiz.getAttribute("Serie")).isEqualTo("A");
		assertThat(raiz.getAttribute("Folio")).isEqualTo("000124");
		assertThat(raiz.getAttribute("Fecha")).isEqualTo("2026-11-26T12:00:00");
		assertThat(raiz.getAttribute("FormaPago")).isEqualTo("28");
		assertThat(raiz.getAttribute("MetodoPago")).isEqualTo("PUE");
		assertThat(raiz.getAttribute("TipoDeComprobante")).isEqualTo("I");
		assertThat(raiz.getAttribute("Moneda")).isEqualTo("MXN");
		assertThat(raiz.getAttribute("Exportacion")).isEqualTo("01");
		assertThat(raiz.getAttribute("LugarExpedicion")).isEqualTo("42501");
		assertThat(raiz.getAttribute("SubTotal")).isEqualTo("1422.41");
		assertThat(raiz.getAttribute("Total")).isEqualTo("1650.00");
		assertThat(raiz.getAttribute("Sello")).isEqualTo("SIMULADO");

		Element emisor = unico(xml, TimbradorSimulado.CFDI, "Emisor");
		assertThat(emisor.getAttribute("Rfc")).isEqualTo("EKU9003173C9");
		assertThat(emisor.getAttribute("RegimenFiscal")).isEqualTo("601");
		Element receptor = unico(xml, TimbradorSimulado.CFDI, "Receptor");
		assertThat(receptor.getAttribute("Rfc")).isEqualTo("GODE561231GR8");
		assertThat(receptor.getAttribute("Nombre")).isEqualTo("DOLORES GÓMEZ");
		assertThat(receptor.getAttribute("DomicilioFiscalReceptor")).isEqualTo("37000");
		assertThat(receptor.getAttribute("RegimenFiscalReceptor")).isEqualTo("612");
		assertThat(receptor.getAttribute("UsoCFDI")).isEqualTo("G03");

		NodeList conceptos = xml.getElementsByTagNameNS(TimbradorSimulado.CFDI, "Concepto");
		assertThat(conceptos.getLength()).isEqualTo(2);
		Element afinacion = (Element) conceptos.item(0);
		assertThat(afinacion.getAttribute("ClaveProdServ")).isEqualTo("78181500");
		assertThat(afinacion.getAttribute("ClaveUnidad")).isEqualTo("E48");
		assertThat(afinacion.getAttribute("Descripcion")).isEqualTo("Afinación");
		assertThat(afinacion.getAttribute("Importe")).isEqualTo("1034.48");
		assertThat(afinacion.getAttribute("ObjetoImp")).isEqualTo("02");
		Element filtro = (Element) conceptos.item(1);
		assertThat(filtro.getAttribute("ClaveProdServ")).isEqualTo("25174800");
		assertThat(filtro.getAttribute("Cantidad")).isEqualTo("3");

		Element impuestos = (Element) raiz.getElementsByTagNameNS(TimbradorSimulado.CFDI, "Impuestos").item(2);
		assertThat(impuestos.getParentNode()).isSameAs(raiz);
		assertThat(impuestos.getAttribute("TotalImpuestosTrasladados")).isEqualTo("227.59");
	}

	@Test
	void elTimbreEsSimuladoYLoDice() throws Exception {
		Timbre timbre = timbrador.timbrar(comprobante(FISICA, Comprobante.FORMA_CREDITO));

		Element tfd = unico(leer(timbre.xml()), TimbradorSimulado.TFD, "TimbreFiscalDigital");
		assertThat(tfd.getAttribute("Version")).isEqualTo("1.1");
		assertThat(tfd.getAttribute("UUID")).isEqualTo(timbre.uuid().toString().toUpperCase(Locale.ROOT));
		assertThat(tfd.getAttribute("FechaTimbrado")).isEqualTo("2026-11-26T12:00:00");
		assertThat(tfd.getAttribute("SelloSAT")).isEqualTo("SIMULADO");
		assertThat(tfd.getAttribute("SelloCFD")).isEqualTo("SIMULADO");
		assertThat(tfd.getAttribute("Leyenda")).isEqualTo("Factura simulada, sin validez fiscal");
		assertThat(timbre.timbradoEn()).isEqualTo(Instant.parse("2026-11-26T18:00:00Z"));
		assertThat(timbre.rfcProveedor()).isEqualTo(TimbradorSimulado.RFC_PROVEEDOR);
		assertThat(timbrador.timbrar(comprobante(FISICA, Comprobante.FORMA_CREDITO)).uuid())
				.isNotEqualTo(timbre.uuid());
	}

	@Test
	void rechazaLaRazonSocialConRegimenSocietarioYUnCodigoPostalQueNoExiste() {
		Comprobante.Receptor conRegimen = new Comprobante.Receptor("EKU9003173C9",
				"ESCUELA KEMPER URGATE, S.A. DE C.V.", "00500", "601", "G03");

		assertThatThrownBy(() -> timbrador.timbrar(comprobante(conRegimen, Comprobante.FORMA_CREDITO)))
				.isInstanceOfSatisfying(CamposNoProcesablesException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo("TIMBRADO_RECHAZADO");
					assertThat(e.getCampos()).containsOnlyKeys("razonSocial", "codigoPostal");
				});
	}

	@Test
	void elRegimenSocietarioSoloSeBuscaAlFinalYEnPalabrasCompletas() {
		assertThat(TimbradorSimulado.traeRegimenSocietario("ESCUELA KEMPER URGATE SA DE CV")).isTrue();
		assertThat(TimbradorSimulado.traeRegimenSocietario("ACME S. DE R.L. DE C.V.")).isTrue();
		assertThat(TimbradorSimulado.traeRegimenSocietario("TALLER DEL BAJIO SAPI DE CV")).isTrue();
		assertThat(TimbradorSimulado.traeRegimenSocietario("MUEBLES ROSA")).isFalse();
		assertThat(TimbradorSimulado.traeRegimenSocietario("SA DE CV")).isFalse();
		assertThat(TimbradorSimulado.traeRegimenSocietario("ESCUELA KEMPER URGATE")).isFalse();
		Comprobante.Receptor fisicaConSa = new Comprobante.Receptor("GODE561231GR8", "DOLORES SA", "37000", "612",
				"G03");
		assertThat(TimbradorSimulado.rechazos(fisicaConSa)).isEmpty();
	}

	private static Comprobante comprobante(Comprobante.Receptor receptor, String formaPago) {
		return new Comprobante("A", 124, LocalDateTime.of(2026, 11, 26, 12, 0), EMISOR, receptor,
				List.of(Concepto.manoDeObra("Afinación", 1, new BigDecimal("1200.00")),
						Concepto.refaccion("Filtro de aceite", 3, new BigDecimal("450.00"))),
				formaPago);
	}

	private static Document leer(byte[] xml) throws Exception {
		DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
		fabrica.setNamespaceAware(true);
		fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return fabrica.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
	}

	private static Element unico(Document xml, String espacio, String nombre) {
		NodeList nodos = xml.getElementsByTagNameNS(espacio, nombre);
		assertThat(nodos.getLength()).as(nombre).isEqualTo(1);
		return (Element) nodos.item(0);
	}
}
