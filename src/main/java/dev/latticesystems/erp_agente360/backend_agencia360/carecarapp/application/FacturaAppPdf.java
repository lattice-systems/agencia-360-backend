package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Timbrador.Timbre;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Comprobante.Concepto;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * El PDF de la factura de un pago de la app (V2-M08.06), armado con OpenPDF
 * como el del ERP: leyenda de factura simulada, emisor y receptor, conceptos,
 * totales, forma y método de pago y el folio fiscal del timbre. Dice dos veces
 * que no tiene validez fiscal (ADR 0009).
 */
@Component
public class FacturaAppPdf {

	public static final String LEYENDA = "Factura simulada, sin validez fiscal";

	static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final DateTimeFormatter FECHA_TIMBRE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
			.withZone(ZONA_AGENCIA);
	private static final Locale MEXICO = Locale.forLanguageTag("es-MX");
	private static final Color GRIS = new Color(0x6B, 0x72, 0x80);
	private static final Color FONDO = new Color(0xF3, 0xF4, 0xF6);
	private static final Color AVISO = new Color(0xB4, 0x23, 0x18);
	private static final Color FONDO_AVISO = new Color(0xFE, 0xF3, 0xF2);

	private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
	private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
	private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 8);
	private static final Font NOTA = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, GRIS);
	private static final Font ALERTA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, AVISO);

	public byte[] armar(Comprobante comprobante, Timbre timbre) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.LETTER, 48, 48, 48, 48);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.addTitle("Factura " + comprobante.folioCompleto());
			documento.addAuthor(comprobante.emisor().nombre());
			documento.addSubject(LEYENDA);
			documento.open();
			documento.add(aviso());
			encabezado(documento, comprobante, timbre);
			documento.add(partes(comprobante));
			documento.add(conceptos(comprobante));
			documento.add(montos(comprobante));
			documento.add(pago(comprobante));
			documento.add(timbre(timbre));
			Paragraph leyenda = new Paragraph(LEYENDA + ". Este documento no es un CFDI: el timbre y los sellos son "
					+ "simulados y no se reportan al SAT.", NOTA);
			leyenda.setSpacingBefore(16);
			documento.add(leyenda);
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el PDF de la factura " + comprobante.folioCompleto(), e);
		} finally {
			documento.close();
		}
		return salida.toByteArray();
	}

	private static PdfPTable aviso() {
		PdfPTable tabla = new PdfPTable(1);
		tabla.setWidthPercentage(100);
		tabla.setSpacingAfter(12);
		PdfPCell celda = new PdfPCell(new Phrase(LEYENDA.toUpperCase(MEXICO), ALERTA));
		celda.setBackgroundColor(FONDO_AVISO);
		celda.setBorderColor(AVISO);
		celda.setHorizontalAlignment(Element.ALIGN_CENTER);
		celda.setPadding(8);
		tabla.addCell(celda);
		return tabla;
	}

	private static void encabezado(Document documento, Comprobante comprobante, Timbre timbre) {
		PdfPTable tabla = new PdfPTable(new float[]{3, 2});
		tabla.setWidthPercentage(100);
		tabla.addCell(sinBorde(new Phrase("Factura", TITULO)));
		PdfPCell folio = sinBorde(new Phrase("Folio " + comprobante.folioCompleto() + "\n"
				+ FECHA.format(comprobante.fecha()) + "\nFolio fiscal: " + timbre.uuid(), NEGRITA));
		folio.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(folio);
		tabla.setSpacingAfter(16);
		documento.add(tabla);
	}

	private static PdfPTable partes(Comprobante comprobante) {
		PdfPTable tabla = new PdfPTable(2);
		tabla.setWidthPercentage(100);
		tabla.setSpacingAfter(16);
		Comprobante.Emisor emisor = comprobante.emisor();
		String rfcEmisor = "RFC: " + emisor.rfc();
		String regimenEmisor = "Régimen fiscal: " + emisor.regimenFiscal();
		String lugar = "Lugar de expedición: " + emisor.codigoPostal();
		tabla.addCell(parte("Emisor", emisor.nombre(), rfcEmisor, regimenEmisor, lugar));
		Comprobante.Receptor receptor = comprobante.receptor();
		String rfcReceptor = "RFC: " + receptor.rfc();
		String domicilio = "Domicilio fiscal (C.P.): " + receptor.codigoPostal();
		String regimenReceptor = "Régimen fiscal: " + receptor.regimenFiscal();
		String uso = "Uso del CFDI: " + receptor.usoCfdi();
		tabla.addCell(parte("Receptor", receptor.nombre(), rfcReceptor, domicilio, regimenReceptor, uso));
		return tabla;
	}

	private static PdfPCell parte(String titulo, String... lineas) {
		Phrase texto = new Phrase();
		texto.add(new Phrase(titulo + "\n", NEGRITA));
		texto.add(new Phrase(String.join("\n", lineas), NORMAL));
		PdfPCell celda = new PdfPCell(texto);
		celda.setPadding(8);
		celda.setBorderColor(FONDO);
		return celda;
	}

	private static PdfPTable conceptos(Comprobante comprobante) {
		PdfPTable tabla = new PdfPTable(new float[]{1, 2, 5, 2, 2});
		tabla.setWidthPercentage(100);
		for (String columna : new String[]{"Cant.", "Clave", "Descripción", "Valor unitario", "Importe"}) {
			PdfPCell celda = new PdfPCell(new Phrase(columna, NEGRITA));
			celda.setBackgroundColor(FONDO);
			celda.setBorder(Rectangle.NO_BORDER);
			celda.setPadding(6);
			tabla.addCell(celda);
		}
		for (Concepto concepto : comprobante.conceptos()) {
			tabla.addCell(renglon(Integer.toString(concepto.cantidad()), Element.ALIGN_LEFT, NORMAL));
			tabla.addCell(
					renglon(concepto.claveProdServ() + "\n" + concepto.claveUnidad(), Element.ALIGN_LEFT, PEQUENA));
			tabla.addCell(renglon(concepto.descripcion(), Element.ALIGN_LEFT, NORMAL));
			tabla.addCell(renglon(moneda(concepto.valorUnitario()), Element.ALIGN_RIGHT, NORMAL));
			tabla.addCell(renglon(moneda(concepto.importe()), Element.ALIGN_RIGHT, NORMAL));
		}
		return tabla;
	}

	private static PdfPTable montos(Comprobante comprobante) {
		PdfPTable tabla = new PdfPTable(new float[]{6, 2});
		tabla.setWidthPercentage(100);
		tabla.setSpacingBefore(8);
		total(tabla, "Subtotal", comprobante.subtotal(), NORMAL);
		total(tabla, "IVA 16 %", comprobante.iva(), NORMAL);
		total(tabla, "Total (MXN)", comprobante.total(), NEGRITA);
		return tabla;
	}

	private static Paragraph pago(Comprobante comprobante) {
		String forma = Comprobante.FORMA_DEBITO.equals(comprobante.formaPago())
				? "28 · Tarjeta de débito"
				: "04 · Tarjeta de crédito";
		Paragraph texto = new Paragraph("Forma de pago: " + forma + "   ·   Método de pago: " + Comprobante.METODO_PAGO
				+ " · Pago en una sola exhibición   ·   Moneda: " + Comprobante.MONEDA, NORMAL);
		texto.setSpacingBefore(12);
		return texto;
	}

	private static Paragraph timbre(Timbre timbre) {
		Paragraph texto = new Paragraph("Timbre fiscal (simulado) · Folio fiscal: " + timbre.uuid()
				+ " · Fecha de timbrado: " + FECHA_TIMBRE.format(timbre.timbradoEn()) + " · RFC del proveedor: "
				+ timbre.rfcProveedor() + " · Sello digital del emisor: SIMULADO · Sello del SAT: SIMULADO", NOTA);
		texto.setSpacingBefore(12);
		return texto;
	}

	private static void total(PdfPTable tabla, String etiqueta, BigDecimal monto, Font fuente) {
		PdfPCell nombre = sinBorde(new Phrase(etiqueta, fuente));
		nombre.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(nombre);
		PdfPCell valor = sinBorde(new Phrase(moneda(monto), fuente));
		valor.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(valor);
	}

	private static PdfPCell renglon(String texto, int alineacion, Font fuente) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
		celda.setBorder(Rectangle.BOTTOM);
		celda.setBorderColor(FONDO);
		celda.setPadding(6);
		celda.setHorizontalAlignment(alineacion);
		return celda;
	}

	private static PdfPCell sinBorde(Phrase texto) {
		PdfPCell celda = new PdfPCell(texto);
		celda.setBorder(Rectangle.NO_BORDER);
		celda.setPaddingBottom(4);
		return celda;
	}

	private static String moneda(BigDecimal monto) {
		NumberFormat formato = NumberFormat.getCurrencyInstance(MEXICO);
		formato.setMaximumFractionDigits(monto.scale() > 2 ? 6 : 2);
		return formato.format(monto);
	}
}
