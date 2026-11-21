package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
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
import dev.latticesystems.erp_agente360.backend_agencia360.shared.pdf.FotosDeAutos;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Arma el PDF del ticket de un servicio para la app (APP-3.2): marca, folio y
 * fecha, la tarjeta del vehículo con la foto de su modelo, los renglones, los
 * montos con el IVA desglosado y el próximo servicio. No tiene validez fiscal.
 */
@Component
public class TicketPdf {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Locale MEXICO = Locale.forLanguageTag("es-MX");
	private static final Color AZUL = new Color(0x1D, 0x35, 0x57);
	private static final Color TURQUESA = new Color(0x2E, 0xC4, 0xB6);
	private static final Color GRIS = new Color(0x6B, 0x72, 0x80);
	private static final Color FONDO = new Color(0xF3, 0xF4, 0xF6);
	private static final Color NARANJA = new Color(0xE0, 0x7A, 0x1F);
	private static final Font MARCA = font(FontFactory.HELVETICA_BOLD, 20, Color.WHITE);
	private static final Font SUBMARCA = font(FontFactory.HELVETICA, 9, new Color(0xCF, 0xE9, 0xE6));
	private static final Font BLANCO = font(FontFactory.HELVETICA_BOLD, 14, Color.WHITE);
	private static final Font BLANCO_P = font(FontFactory.HELVETICA, 9, Color.WHITE);
	private static final Font SECCION = font(FontFactory.HELVETICA_BOLD, 9, TURQUESA.darker());
	private static final Font MODELO = font(FontFactory.HELVETICA_BOLD, 15, AZUL);
	private static final Font NEGRITA = font(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
	private static final Font NORMAL = font(FontFactory.HELVETICA, 10, Color.BLACK);
	private static final Font ETIQUETA = font(FontFactory.HELVETICA, 8, GRIS);
	private static final Font DATO = font(FontFactory.HELVETICA_BOLD, 10, AZUL);
	private static final Font ENCABEZADO = font(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
	private static final Font TOTAL = font(FontFactory.HELVETICA_BOLD, 18, AZUL);
	private static final Font NOTA = font(FontFactory.HELVETICA, 8, GRIS);

	private static Font font(String familia, float tamano, Color color) {
		return FontFactory.getFont(familia, tamano, Font.NORMAL, color);
	}

	/**
	 * @param vehiculo
	 *            puede faltar; entonces el ticket no trae sus datos
	 */
	public byte[] armar(ServicioDetalle servicio, VehiculoDeCliente vehiculo) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4, 40, 40, 36, 36);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.addTitle("Ticket de servicio " + servicio.id());
			documento.open();
			documento.add(encabezado(servicio));
			documento.add(espacio(14));
			if (vehiculo != null) {
				documento.add(tarjetaVehiculo(vehiculo));
				documento.add(espacio(16));
			}
			documento.add(titulo("CONCEPTOS DEL SERVICIO"));
			documento.add(renglones(servicio));
			documento.add(espacio(10));
			documento.add(montos(servicio));
			documento.add(espacio(14));
			documento.add(proximo(servicio));
			documento.add(espacio(18));
			documento.add(pie());
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el ticket del servicio " + servicio.id(), e);
		} finally {
			documento.close();
		}
		return salida.toByteArray();
	}

	private static Paragraph espacio(float alto) {
		Paragraph p = new Paragraph(" ", font(FontFactory.HELVETICA, 1, Color.WHITE));
		p.setSpacingAfter(alto);
		return p;
	}

	private static Paragraph titulo(String texto) {
		Paragraph p = new Paragraph(texto, SECCION);
		p.setSpacingAfter(5);
		return p;
	}

	private static PdfPTable encabezado(ServicioDetalle servicio) {
		PdfPTable tabla = new PdfPTable(new float[]{3, 2});
		tabla.setWidthPercentage(100);
		PdfPCell marca = new PdfPCell();
		marca.setBackgroundColor(AZUL);
		marca.setBorder(Rectangle.BOTTOM);
		marca.setBorderColor(TURQUESA);
		marca.setBorderWidthBottom(4);
		marca.setPadding(14);
		marca.addElement(new Paragraph("Agencia360 · CareCar", MARCA));
		marca.addElement(new Paragraph("TICKET DE SERVICIO", SUBMARCA));
		tabla.addCell(marca);
		PdfPCell folio = new PdfPCell();
		folio.setBackgroundColor(AZUL);
		folio.setBorder(Rectangle.BOTTOM);
		folio.setBorderColor(TURQUESA);
		folio.setBorderWidthBottom(4);
		folio.setPadding(14);
		Paragraph f = new Paragraph("Folio " + servicio.id(), BLANCO);
		f.setAlignment(Element.ALIGN_RIGHT);
		Paragraph d = new Paragraph("Fecha: " + FECHA.format(servicio.fecha()), BLANCO_P);
		d.setAlignment(Element.ALIGN_RIGHT);
		Paragraph k = new Paragraph("Kilometraje: " + miles(servicio.kilometraje()) + " km", BLANCO_P);
		k.setAlignment(Element.ALIGN_RIGHT);
		folio.addElement(f);
		folio.addElement(d);
		folio.addElement(k);
		tabla.addCell(folio);
		return tabla;
	}

	private static PdfPTable tarjetaVehiculo(VehiculoDeCliente v) {
		PdfPTable tarjeta = new PdfPTable(new float[]{5, 6});
		tarjeta.setWidthPercentage(100);
		PdfPCell foto = new PdfPCell();
		foto.setBackgroundColor(FONDO);
		foto.setBorder(Rectangle.NO_BORDER);
		foto.setPadding(8);
		foto.setVerticalAlignment(Element.ALIGN_MIDDLE);
		Image imagen = FotosDeAutos.de(v.modelo());
		if (imagen != null) {
			imagen.scaleToFit(210, 130);
			imagen.setAlignment(Element.ALIGN_CENTER);
			foto.addElement(imagen);
		} else {
			Paragraph p = new Paragraph(v.modelo(), MODELO);
			p.setAlignment(Element.ALIGN_CENTER);
			foto.addElement(p);
			foto.setFixedHeight(100);
			foto.setVerticalAlignment(Element.ALIGN_MIDDLE);
		}
		tarjeta.addCell(foto);

		PdfPCell datos = new PdfPCell();
		datos.setBackgroundColor(FONDO);
		datos.setBorder(Rectangle.NO_BORDER);
		datos.setPadding(10);
		datos.addElement(new Paragraph("TU VEHÍCULO", SECCION));
		String nombre = (v.marca() != null ? v.marca() + " " : "") + v.modelo();
		Paragraph m = new Paragraph(nombre, MODELO);
		m.setSpacingAfter(6);
		datos.addElement(m);
		PdfPTable rejilla = new PdfPTable(2);
		rejilla.setWidthPercentage(100);
		dato(rejilla, "AÑO", String.valueOf(v.anio()));
		dato(rejilla, "VERSIÓN", v.version());
		dato(rejilla, "PLACAS", v.placas());
		dato(rejilla, "KILOMETRAJE", v.kilometraje() == null ? null : miles(v.kilometraje()) + " km");
		datos.addElement(rejilla);
		Paragraph vin = new Paragraph();
		vin.setSpacingBefore(2);
		vin.add(new Chunk("VIN  ", ETIQUETA));
		vin.add(new Chunk(v.vin(), font(FontFactory.COURIER_BOLD, 9, AZUL)));
		datos.addElement(vin);
		tarjeta.addCell(datos);
		return tarjeta;
	}

	private static void dato(PdfPTable rejilla, String etiqueta, String valor) {
		PdfPCell c = new PdfPCell();
		c.setBorder(Rectangle.NO_BORDER);
		c.setPaddingLeft(0);
		c.setPaddingBottom(5);
		c.addElement(new Paragraph(etiqueta, ETIQUETA));
		c.addElement(new Paragraph(valor == null || valor.isBlank() ? "—" : valor, DATO));
		rejilla.addCell(c);
	}

	private static PdfPTable renglones(ServicioDetalle servicio) {
		PdfPTable tabla = new PdfPTable(new float[]{2.4f, 5, 1.1f, 2.2f, 2.3f});
		tabla.setWidthPercentage(100);
		String[] columnas = {"Tipo", "Concepto", "Cant.", "Precio", "Importe"};
		for (int i = 0; i < columnas.length; i++) {
			PdfPCell celda = new PdfPCell(new Phrase(columnas[i], ENCABEZADO));
			celda.setBackgroundColor(AZUL);
			celda.setBorder(Rectangle.NO_BORDER);
			celda.setPadding(6);
			celda.setHorizontalAlignment(i >= 2 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
			tabla.addCell(celda);
		}
		tabla.setHeaderRows(1);
		int n = 0;
		for (ServicioDetalle.Renglon renglon : servicio.renglones()) {
			Color fondo = n++ % 2 == 0 ? Color.WHITE : FONDO;
			boolean mano = renglon.tipo() == TipoRenglon.MANO_DE_OBRA;
			Font etiqueta = font(FontFactory.HELVETICA_BOLD, 8, mano ? TURQUESA.darker() : NARANJA);
			tabla.addCell(celda(mano ? "Mano de obra" : "Refacción", Element.ALIGN_LEFT, fondo, etiqueta));
			tabla.addCell(celda(renglon.concepto(), Element.ALIGN_LEFT, fondo, NORMAL));
			tabla.addCell(celda(String.valueOf(renglon.cantidad()), Element.ALIGN_RIGHT, fondo, NORMAL));
			tabla.addCell(celda(moneda(renglon.precioUnitario()), Element.ALIGN_RIGHT, fondo, NORMAL));
			tabla.addCell(celda(moneda(renglon.importe()), Element.ALIGN_RIGHT, fondo, NEGRITA));
		}
		return tabla;
	}

	private static PdfPTable montos(ServicioDetalle servicio) {
		PdfPTable tabla = new PdfPTable(new float[]{5, 3});
		tabla.setWidthPercentage(100);
		total(tabla, "Subtotal", servicio.subtotal(), NORMAL);
		total(tabla, "IVA 16 %", servicio.iva(), NORMAL);
		PdfPCell nombre = new PdfPCell(new Phrase("TOTAL (MXN)", NEGRITA));
		nombre.setBorder(Rectangle.TOP);
		nombre.setBorderColor(TURQUESA);
		nombre.setBorderWidthTop(2);
		nombre.setHorizontalAlignment(Element.ALIGN_RIGHT);
		nombre.setVerticalAlignment(Element.ALIGN_MIDDLE);
		nombre.setPadding(6);
		tabla.addCell(nombre);
		PdfPCell valor = new PdfPCell(new Phrase(moneda(servicio.total()), TOTAL));
		valor.setBorder(Rectangle.TOP);
		valor.setBorderColor(TURQUESA);
		valor.setBorderWidthTop(2);
		valor.setHorizontalAlignment(Element.ALIGN_RIGHT);
		valor.setPadding(6);
		tabla.addCell(valor);
		return tabla;
	}

	private static void total(PdfPTable tabla, String etiqueta, BigDecimal monto, Font fuente) {
		PdfPCell nombre = new PdfPCell(new Phrase(etiqueta, fuente));
		nombre.setBorder(Rectangle.NO_BORDER);
		nombre.setHorizontalAlignment(Element.ALIGN_RIGHT);
		nombre.setPadding(3);
		tabla.addCell(nombre);
		PdfPCell valor = new PdfPCell(new Phrase(moneda(monto), fuente));
		valor.setBorder(Rectangle.NO_BORDER);
		valor.setHorizontalAlignment(Element.ALIGN_RIGHT);
		valor.setPadding(3);
		tabla.addCell(valor);
	}

	private static PdfPTable proximo(ServicioDetalle servicio) {
		int siguiente = (servicio.kilometraje() / 10000 + 1) * 10000;
		PdfPTable tabla = new PdfPTable(1);
		tabla.setWidthPercentage(100);
		PdfPCell c = new PdfPCell();
		c.setBackgroundColor(new Color(0xE6, 0xF8, 0xF6));
		c.setBorder(Rectangle.LEFT);
		c.setBorderColor(TURQUESA);
		c.setBorderWidthLeft(4);
		c.setPadding(10);
		c.addElement(new Paragraph("PRÓXIMO SERVICIO", SECCION));
		c.addElement(new Paragraph("A los " + miles(siguiente) + " km", MODELO));
		c.addElement(new Paragraph("Gracias por tu preferencia. ¡Te esperamos pronto!", NORMAL));
		tabla.addCell(c);
		return tabla;
	}

	private static PdfPTable pie() {
		PdfPTable tabla = new PdfPTable(1);
		tabla.setWidthPercentage(100);
		PdfPCell c = new PdfPCell();
		c.setBorder(Rectangle.TOP);
		c.setBorderColor(FONDO);
		c.setPaddingTop(8);
		Paragraph contacto = new Paragraph("Agencia360 · CareCar  |  +52 000 000 0000  |  contacto@agencia.example.com",
				NOTA);
		contacto.setAlignment(Element.ALIGN_CENTER);
		Paragraph leyenda = new Paragraph("Precios con IVA incluido. Este ticket no es un comprobante fiscal.", NOTA);
		leyenda.setAlignment(Element.ALIGN_CENTER);
		c.addElement(contacto);
		c.addElement(leyenda);
		tabla.addCell(c);
		return tabla;
	}

	private static PdfPCell celda(String texto, int alineacion, Color fondo, Font fuente) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
		celda.setBorder(Rectangle.NO_BORDER);
		celda.setBackgroundColor(fondo);
		celda.setPadding(6);
		celda.setHorizontalAlignment(alineacion);
		return celda;
	}

	private static String miles(int n) {
		return NumberFormat.getIntegerInstance(MEXICO).format(n);
	}

	private static String moneda(BigDecimal monto) {
		return NumberFormat.getCurrencyInstance(MEXICO).format(monto);
	}
}
