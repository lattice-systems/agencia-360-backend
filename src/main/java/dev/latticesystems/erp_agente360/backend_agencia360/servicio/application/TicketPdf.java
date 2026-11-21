package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

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
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Arma el PDF del ticket de un servicio para la app (APP-3.2): folio, fecha y
 * kilometraje, el vehículo, los renglones y los montos con el IVA desglosado,
 * con el mismo estilo que la factura. No tiene validez fiscal.
 */
@Component
public class TicketPdf {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Locale MEXICO = Locale.forLanguageTag("es-MX");
	private static final Color GRIS = new Color(0x6B, 0x72, 0x80);
	private static final Color FONDO = new Color(0xF3, 0xF4, 0xF6);

	private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
	private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
	private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font NOTA = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, GRIS);

	/**
	 * @param vehiculo
	 *            puede faltar; entonces el ticket no trae sus datos
	 */
	public byte[] armar(ServicioDetalle servicio, VehiculoDeCliente vehiculo) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.LETTER, 48, 48, 48, 48);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.addTitle("Ticket de servicio " + servicio.id());
			documento.open();
			documento.add(new Paragraph("Ticket de servicio", TITULO));
			Paragraph datos = new Paragraph("Folio " + servicio.id() + " · " + FECHA.format(servicio.fecha())
					+ " · Kilometraje: " + servicio.kilometraje() + " km", NEGRITA);
			datos.setSpacingAfter(8);
			documento.add(datos);
			if (vehiculo != null) {
				Paragraph unidad = new Paragraph(descripcion(vehiculo), NORMAL);
				unidad.setSpacingAfter(16);
				documento.add(unidad);
			}
			documento.add(renglones(servicio));
			documento.add(montos(servicio));
			Paragraph leyenda = new Paragraph("Precios con IVA incluido. Este ticket no es un comprobante fiscal.",
					NOTA);
			leyenda.setSpacingBefore(24);
			documento.add(leyenda);
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el ticket del servicio " + servicio.id(), e);
		} finally {
			documento.close();
		}
		return salida.toByteArray();
	}

	private static String descripcion(VehiculoDeCliente vehiculo) {
		StringBuilder texto = new StringBuilder("Vehículo: ");
		if (vehiculo.marca() != null) {
			texto.append(vehiculo.marca()).append(' ');
		}
		texto.append(vehiculo.modelo()).append(' ').append(vehiculo.anio());
		if (vehiculo.version() != null) {
			texto.append(' ').append(vehiculo.version());
		}
		texto.append("\nNúmero de serie (VIN): ").append(vehiculo.vin());
		if (vehiculo.placas() != null) {
			texto.append(" · Placas: ").append(vehiculo.placas());
		}
		return texto.toString();
	}

	private static PdfPTable renglones(ServicioDetalle servicio) {
		PdfPTable tabla = new PdfPTable(new float[]{2, 5, 1, 2, 2});
		tabla.setWidthPercentage(100);
		for (String columna : new String[]{"Tipo", "Concepto", "Cant.", "Precio", "Importe"}) {
			PdfPCell celda = new PdfPCell(new Phrase(columna, NEGRITA));
			celda.setBackgroundColor(FONDO);
			celda.setBorder(Rectangle.NO_BORDER);
			celda.setPadding(6);
			tabla.addCell(celda);
		}
		for (ServicioDetalle.Renglon renglon : servicio.renglones()) {
			tabla.addCell(celda(renglon.tipo() == TipoRenglon.MANO_DE_OBRA ? "Mano de obra" : "Refacción",
					Element.ALIGN_LEFT));
			tabla.addCell(celda(renglon.concepto(), Element.ALIGN_LEFT));
			tabla.addCell(celda(String.valueOf(renglon.cantidad()), Element.ALIGN_RIGHT));
			tabla.addCell(celda(moneda(renglon.precioUnitario()), Element.ALIGN_RIGHT));
			tabla.addCell(celda(moneda(renglon.importe()), Element.ALIGN_RIGHT));
		}
		return tabla;
	}

	private static PdfPTable montos(ServicioDetalle servicio) {
		PdfPTable tabla = new PdfPTable(new float[]{6, 2});
		tabla.setWidthPercentage(100);
		tabla.setSpacingBefore(8);
		total(tabla, "Subtotal", servicio.subtotal(), NORMAL);
		total(tabla, "IVA 16 %", servicio.iva(), NORMAL);
		total(tabla, "Total (MXN)", servicio.total(), NEGRITA);
		return tabla;
	}

	private static void total(PdfPTable tabla, String etiqueta, BigDecimal monto, Font fuente) {
		PdfPCell nombre = new PdfPCell(new Phrase(etiqueta, fuente));
		nombre.setBorder(Rectangle.NO_BORDER);
		nombre.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(nombre);
		PdfPCell valor = new PdfPCell(new Phrase(moneda(monto), fuente));
		valor.setBorder(Rectangle.NO_BORDER);
		valor.setHorizontalAlignment(Element.ALIGN_RIGHT);
		tabla.addCell(valor);
	}

	private static PdfPCell celda(String texto, int alineacion) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, NORMAL));
		celda.setBorder(Rectangle.BOTTOM);
		celda.setBorderColor(FONDO);
		celda.setPadding(6);
		celda.setHorizontalAlignment(alineacion);
		return celda;
	}

	private static String moneda(BigDecimal monto) {
		return NumberFormat.getCurrencyInstance(MEXICO).format(monto);
	}
}
