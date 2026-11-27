package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Timbrador;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante.Concepto;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposNoProcesablesException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Timbrador simulado de la ADR 0009 (V2-M08.06): no hay PAC, cuenta ni red.
 * Revisa el receptor como lo haría un PAC, arma el XML con la estructura de
 * CFDI 4.0 y le agrega el complemento {@code TimbreFiscalDigital} 1.1 con un
 * UUID aleatorio y sellos {@code SIMULADO}. La factura no tiene validez fiscal
 * y lo dice.
 */
@Component
public class TimbradorSimulado implements Timbrador {

	public static final String CFDI = "http://www.sat.gob.mx/cfd/4";
	public static final String TFD = "http://www.sat.gob.mx/TimbreFiscalDigital";
	public static final String SELLO = "SIMULADO";
	public static final String LEYENDA = "Factura simulada, sin validez fiscal";

	/** RFC de un proveedor de certificación ficticio. */
	public static final String RFC_PROVEEDOR = "SIM010101AA1";

	static final String SIN_CERTIFICADO = "00000000000000000000";

	private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
	private static final String XMLNS = "http://www.w3.org/2000/xmlns/";
	private static final String ESQUEMAS = CFDI + " http://www.sat.gob.mx/sitio_internet/cfd/4/cfdv40.xsd " + TFD
			+ " http://www.sat.gob.mx/sitio_internet/cfd/TimbreFiscalDigital/TimbreFiscalDigitalv11.xsd";
	private static final String IMPUESTO_IVA = "002";
	private static final String OBJETO_DE_IMPUESTO = "02";
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	/** El código postal más bajo que existe en México. */
	private static final int PRIMER_CODIGO_POSTAL = 1000;

	/**
	 * Régimen societario sin espacios ni puntos: CFDI 4.0 pide el nombre de una
	 * persona moral como en la constancia, sin él.
	 */
	private static final Set<String> REGIMENES_SOCIETARIOS = Set.of("SA", "SADECV", "SAB", "SABDECV", "SAPI",
			"SAPIDECV", "SAS", "SASDECV", "SDERL", "SDERLDECV", "SDERLMI", "SC", "SCDERL", "AC", "SCP", "SPR",
			"SPRDERL");
	/** «S. DE R.L. DE C.V.» son siete palabras: se deja una de holgura. */
	private static final int PALABRAS_MAXIMAS_DEL_REGIMEN = 8;

	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public TimbradorSimulado() {
		this(Clock.systemUTC(), ZoneId.of("America/Mexico_City"));
	}

	/** Con un reloj fijo, para las pruebas. */
	public TimbradorSimulado(Clock reloj, ZoneId zona) {
		this.reloj = reloj;
		this.zona = zona;
	}

	@Override
	public Timbre timbrar(Comprobante comprobante) {
		Map<String, String> rechazos = rechazos(comprobante.receptor());
		if (!rechazos.isEmpty()) {
			throw new CamposNoProcesablesException(CodigosError.TIMBRADO_RECHAZADO, rechazos);
		}
		Instant ahora = reloj.instant().truncatedTo(ChronoUnit.SECONDS);
		UUID uuid = UUID.randomUUID();
		byte[] xml = xml(comprobante, uuid, LocalDateTime.ofInstant(ahora, zona));
		return new Timbre(uuid, ahora, RFC_PROVEEDOR, xml);
	}

	/**
	 * Lo que un PAC rechazaría del receptor, por campo del perfil fiscal: el nombre
	 * de una persona moral con su régimen societario y un código postal que no
	 * existe.
	 */
	static Map<String, String> rechazos(Comprobante.Receptor receptor) {
		Map<String, String> rechazos = new LinkedHashMap<>();
		if (receptor.esMoral() && traeRegimenSocietario(receptor.nombre())) {
			rechazos.put("razonSocial",
					"Escribe la razón social sin el régimen societario (por ejemplo sin «S.A. de C.V.»), "
							+ "como aparece en tu constancia de situación fiscal");
		}
		if (!codigoPostalExiste(receptor.codigoPostal())) {
			rechazos.put("codigoPostal",
					"Ese código postal no existe en el catálogo del SAT; usa el de tu constancia de situación fiscal");
		}
		return rechazos;
	}

	static boolean traeRegimenSocietario(String nombre) {
		String limpio = nombre.toUpperCase(Locale.ROOT).replace('.', ' ').replace(',', ' ').strip();
		List<String> palabras = Arrays.stream(limpio.split(" ")).filter(palabra -> !palabra.isEmpty()).toList();
		for (int cuantas = 1; cuantas <= Math.min(PALABRAS_MAXIMAS_DEL_REGIMEN, palabras.size() - 1); cuantas++) {
			String terminacion = String.join("", palabras.subList(palabras.size() - cuantas, palabras.size()));
			if (REGIMENES_SOCIETARIOS.contains(terminacion)) {
				return true;
			}
		}
		return false;
	}

	private static boolean codigoPostalExiste(String codigoPostal) {
		if (codigoPostal == null || codigoPostal.length() != 5 || !codigoPostal.chars().allMatch(Character::isDigit)) {
			return false;
		}
		return Integer.parseInt(codigoPostal) >= PRIMER_CODIGO_POSTAL;
	}

	private static byte[] xml(Comprobante comprobante, UUID uuid, LocalDateTime timbradoEn) {
		Document documento = documentoNuevo();
		Element raiz = documento.createElementNS(CFDI, "cfdi:Comprobante");
		raiz.setAttributeNS(XMLNS, "xmlns:xsi", XSI);
		raiz.setAttributeNS(XMLNS, "xmlns:tfd", TFD);
		raiz.setAttributeNS(XSI, "xsi:schemaLocation", ESQUEMAS);
		raiz.setAttribute("Version", Comprobante.VERSION);
		raiz.setAttribute("Serie", comprobante.serie());
		raiz.setAttribute("Folio", comprobante.folioSinSerie());
		raiz.setAttribute("Fecha", FECHA.format(comprobante.fecha()));
		raiz.setAttribute("Sello", SELLO);
		raiz.setAttribute("FormaPago", comprobante.formaPago());
		raiz.setAttribute("NoCertificado", SIN_CERTIFICADO);
		raiz.setAttribute("Certificado", SELLO);
		raiz.setAttribute("SubTotal", dinero(comprobante.subtotal()));
		raiz.setAttribute("Moneda", Comprobante.MONEDA);
		raiz.setAttribute("Total", dinero(comprobante.total()));
		raiz.setAttribute("TipoDeComprobante", Comprobante.TIPO_INGRESO);
		raiz.setAttribute("Exportacion", Comprobante.EXPORTACION_NO_APLICA);
		raiz.setAttribute("MetodoPago", Comprobante.METODO_PAGO);
		raiz.setAttribute("LugarExpedicion", comprobante.emisor().codigoPostal());
		documento.appendChild(raiz);

		Element emisor = hijo(raiz, "cfdi:Emisor");
		emisor.setAttribute("Rfc", comprobante.emisor().rfc());
		emisor.setAttribute("Nombre", comprobante.emisor().nombre());
		emisor.setAttribute("RegimenFiscal", comprobante.emisor().regimenFiscal());

		Element receptor = hijo(raiz, "cfdi:Receptor");
		receptor.setAttribute("Rfc", comprobante.receptor().rfc());
		receptor.setAttribute("Nombre", comprobante.receptor().nombre());
		receptor.setAttribute("DomicilioFiscalReceptor", comprobante.receptor().codigoPostal());
		receptor.setAttribute("RegimenFiscalReceptor", comprobante.receptor().regimenFiscal());
		receptor.setAttribute("UsoCFDI", comprobante.receptor().usoCfdi());

		Element conceptos = hijo(raiz, "cfdi:Conceptos");
		for (Concepto renglon : comprobante.conceptos()) {
			concepto(conceptos, renglon);
		}

		Element impuestos = hijo(raiz, "cfdi:Impuestos");
		impuestos.setAttribute("TotalImpuestosTrasladados", dinero(comprobante.iva()));
		Element traslado = hijo(hijo(impuestos, "cfdi:Traslados"), "cfdi:Traslado");
		traslado.setAttribute("Base", dinero(comprobante.subtotal()));
		traslado.setAttribute("Impuesto", IMPUESTO_IVA);
		traslado.setAttribute("TipoFactor", "Tasa");
		traslado.setAttribute("TasaOCuota", Concepto.TASA_IVA.toPlainString());
		traslado.setAttribute("Importe", dinero(comprobante.iva()));

		Element complemento = hijo(raiz, "cfdi:Complemento");
		Element timbre = documento.createElementNS(TFD, "tfd:TimbreFiscalDigital");
		timbre.setAttributeNS(XSI, "xsi:schemaLocation",
				TFD + " http://www.sat.gob.mx/sitio_internet/cfd/TimbreFiscalDigital/TimbreFiscalDigitalv11.xsd");
		timbre.setAttribute("Version", "1.1");
		timbre.setAttribute("UUID", uuid.toString().toUpperCase(Locale.ROOT));
		timbre.setAttribute("FechaTimbrado", FECHA.format(timbradoEn));
		timbre.setAttribute("RfcProvCertif", RFC_PROVEEDOR);
		timbre.setAttribute("Leyenda", LEYENDA);
		timbre.setAttribute("SelloCFD", SELLO);
		timbre.setAttribute("NoCertificadoSAT", SIN_CERTIFICADO);
		timbre.setAttribute("SelloSAT", SELLO);
		complemento.appendChild(timbre);
		return escribir(documento);
	}

	private static void concepto(Element conceptos, Concepto renglon) {
		Element concepto = hijo(conceptos, "cfdi:Concepto");
		concepto.setAttribute("ClaveProdServ", renglon.claveProdServ());
		concepto.setAttribute("Cantidad", Integer.toString(renglon.cantidad()));
		concepto.setAttribute("ClaveUnidad", renglon.claveUnidad());
		concepto.setAttribute("Unidad", renglon.unidad());
		concepto.setAttribute("Descripcion", renglon.descripcion());
		concepto.setAttribute("ValorUnitario", renglon.valorUnitario().toPlainString());
		concepto.setAttribute("Importe", dinero(renglon.importe()));
		concepto.setAttribute("ObjetoImp", OBJETO_DE_IMPUESTO);
		Element traslado = hijo(hijo(hijo(concepto, "cfdi:Impuestos"), "cfdi:Traslados"), "cfdi:Traslado");
		traslado.setAttribute("Base", dinero(renglon.importe()));
		traslado.setAttribute("Impuesto", IMPUESTO_IVA);
		traslado.setAttribute("TipoFactor", "Tasa");
		traslado.setAttribute("TasaOCuota", Concepto.TASA_IVA.toPlainString());
		traslado.setAttribute("Importe", dinero(renglon.iva()));
	}

	private static Element hijo(Element padre, String nombre) {
		Element hijo = padre.getOwnerDocument().createElementNS(CFDI, nombre);
		padre.appendChild(hijo);
		return hijo;
	}

	private static String dinero(BigDecimal monto) {
		return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private static Document documentoNuevo() {
		try {
			DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
			fabrica.setNamespaceAware(true);
			fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			return fabrica.newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("No se pudo armar el XML de la factura", e);
		}
	}

	private static byte[] escribir(Document documento) {
		try {
			TransformerFactory fabrica = TransformerFactory.newInstance();
			fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			Transformer transformador = fabrica.newTransformer();
			transformador.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformador.setOutputProperty(OutputKeys.INDENT, "yes");
			documento.setXmlStandalone(true);
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			transformador.transform(new DOMSource(documento), new StreamResult(salida));
			return salida.toByteArray();
		} catch (TransformerException e) {
			throw new IllegalStateException("No se pudo escribir el XML de la factura", e);
		}
	}
}
