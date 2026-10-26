package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

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
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.DatosFactura.Parte;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Arma el PDF de la factura (ERP-1.3): emisor, folio y fecha, receptor, el
 * renglón de la unidad y los montos. No tiene validez fiscal: es folio interno,
 * sin timbrado (decisión 6 del modelo de datos).
 */
@Component
public class FacturaPdf {

	/** La agencia está en México: la fecha de la factura va en su hora. */
	static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ZONA_AGENCIA);
	private static final Locale MEXICO = Locale.forLanguageTag("es-MX");
	private static final Color GRIS = new Color(0x6B, 0x72, 0x80);
	private static final Color FONDO = new Color(0xF3, 0xF4, 0xF6);

	private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
	private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
	private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font NOTA = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, GRIS);

	public byte[] armar(DatosFactura datos) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.LETTER, 48, 48, 48, 48);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.addTitle("Factura " + datos.folio());
			documento.addAuthor(datos.emisor().nombre());
			documento.open();
			encabezado(documento, datos);
			documento.add(partes(datos.emisor(), datos.receptor()));
			documento.add(conceptos(datos));
			documento.add(montos(datos));
			Paragraph leyenda = new Paragraph("Folio interno, sin timbrado: este documento no es un CFDI.", NOTA);
			leyenda.setSpacingBefore(24);
			documento.add(leyenda);
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el PDF de la factura " + datos.folio(), e);
		} finally {
			documento.close();
		}
		return salida.toByteArray();
	}

	private static void encabezado(Document documento, DatosFactura datos) {
		PdfPTable tabla = new PdfPTable(new float[]{3, 2});
		tabla.setWidthPercentage(100);
		PdfPCell titulo = sinBorde(new Phrase("Factura", TITULO));
		tabla.addCell(titulo);
		PdfPCell folio = sinBorde(
				new Phrase("Folio " + datos.folio() + "\n" + FECHA.format(datos.emitidaEn()), NEGRITA));
		folio.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(folio);
		tabla.setSpacingAfter(16);
		documento.add(tabla);
	}

	private static PdfPTable partes(Parte emisor, Parte receptor) {
		PdfPTable tabla = new PdfPTable(2);
		tabla.setWidthPercentage(100);
		tabla.setSpacingAfter(16);
		tabla.addCell(parte("Emisor", emisor));
		tabla.addCell(parte("Receptor", receptor));
		return tabla;
	}

	private static PdfPCell parte(String titulo, Parte parte) {
		Phrase texto = new Phrase();
		texto.add(new Phrase(titulo + "\n", NEGRITA));
		texto.add(new Phrase(parte.nombre() + "\nRFC: " + parte.rfc() + "\nDomicilio fiscal: " + parte.domicilioFiscal()
				+ "\nRégimen fiscal: " + parte.regimenFiscal(), NORMAL));
		PdfPCell celda = new PdfPCell(texto);
		celda.setPadding(8);
		celda.setBorderColor(FONDO);
		return celda;
	}

	private static PdfPTable conceptos(DatosFactura datos) {
		PdfPTable tabla = new PdfPTable(new float[]{1, 5, 2});
		tabla.setWidthPercentage(100);
		for (String columna : new String[]{"Cantidad", "Descripción", "Importe"}) {
			PdfPCell celda = new PdfPCell(new Phrase(columna, NEGRITA));
			celda.setBackgroundColor(FONDO);
			celda.setBorder(Rectangle.NO_BORDER);
			celda.setPadding(6);
			tabla.addCell(celda);
		}
		DatosFactura.Unidad unidad = datos.unidad();
		tabla.addCell(renglon("1", Element.ALIGN_LEFT));
		tabla.addCell(renglon("Vehículo " + unidad.marca() + " " + unidad.modelo() + " " + unidad.version() + "\nTipo: "
				+ unidad.modelo() + " · Modelo: " + unidad.anio() + " · Color: " + unidad.color()
				+ "\nNúmero de serie (VIN): " + unidad.vin(), Element.ALIGN_LEFT));
		tabla.addCell(renglon(moneda(datos.montos().subtotal()), Element.ALIGN_RIGHT));
		return tabla;
	}

	private static PdfPTable montos(DatosFactura datos) {
		PdfPTable tabla = new PdfPTable(new float[]{6, 2});
		tabla.setWidthPercentage(100);
		tabla.setSpacingBefore(8);
		total(tabla, "Subtotal", datos.montos().subtotal(), NORMAL);
		total(tabla, "IVA 16 %", datos.montos().iva(), NORMAL);
		total(tabla, "Total (MXN)", datos.montos().total(), NEGRITA);
		return tabla;
	}

	private static void total(PdfPTable tabla, String etiqueta, BigDecimal monto, Font fuente) {
		PdfPCell nombre = sinBorde(new Phrase(etiqueta, fuente));
		nombre.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(nombre);
		PdfPCell valor = sinBorde(new Phrase(moneda(monto), fuente));
		valor.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(valor);
	}

	private static PdfPCell renglon(String texto, int alineacion) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, NORMAL));
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
		return NumberFormat.getCurrencyInstance(MEXICO).format(monto);
	}
}
