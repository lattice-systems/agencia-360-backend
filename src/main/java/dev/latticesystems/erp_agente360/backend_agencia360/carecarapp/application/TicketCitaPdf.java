package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.pdf.FotosDeAutos;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * PDF del comprobante de una cita (APP-3.3): mismo estilo que el ticket de
 * servicio, con la foto del coche.
 */
@Component
public class TicketCitaPdf {

	private static final Locale MEXICO = Locale.forLanguageTag("es-MX");
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
	private static final Color AZUL = new Color(0x1D, 0x35, 0x57);
	private static final Color TURQUESA = new Color(0x2E, 0xC4, 0xB6);
	private static final Color GRIS = new Color(0x6B, 0x72, 0x80);
	private static final Color FONDO = new Color(0xF3, 0xF4, 0xF6);
	private static final Color VERDE = new Color(0x1E, 0x8E, 0x5A);
	private static final Color ROJO = new Color(0xC0, 0x39, 0x2B);

	private static Font f(String familia, float t, Color c) {
		return FontFactory.getFont(familia, t, Font.NORMAL, c);
	}

	/**
	 * @param contenidoQr
	 *            texto firmado del QR de recepción (V2-M02.11); sin él no se dibuja
	 *            el QR
	 */
	public byte[] armar(Cita cita, VehiculoDeCliente v, String contenidoQr) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
		try {
			PdfWriter.getInstance(doc, salida);
			doc.addTitle("Comprobante de cita " + cita.getId());
			doc.open();
			doc.add(encabezado(cita));
			doc.add(espacio(14));
			doc.add(cuando(cita));
			doc.add(espacio(14));
			if (v != null) {
				doc.add(vehiculo(v));
				doc.add(espacio(14));
			}
			doc.add(lugar());
			doc.add(espacio(14));
			doc.add(recomendaciones());
			doc.add(espacio(18));
			doc.add(pie(cita, contenidoQr));
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el comprobante de la cita " + cita.getId(), e);
		} finally {
			doc.close();
		}
		return salida.toByteArray();
	}

	private static Paragraph espacio(float alto) {
		Paragraph p = new Paragraph(" ", f(FontFactory.HELVETICA, 1, Color.WHITE));
		p.setSpacingAfter(alto);
		return p;
	}

	private static PdfPCell base(Color fondo, int borde, Color colorBorde, float ancho, float padding) {
		PdfPCell c = new PdfPCell();
		c.setBackgroundColor(fondo);
		c.setBorder(borde);
		c.setBorderColor(colorBorde);
		c.setBorderWidth(ancho);
		c.setPadding(padding);
		return c;
	}

	private static Paragraph der(String t, Font fuente) {
		Paragraph p = new Paragraph(t, fuente);
		p.setAlignment(Element.ALIGN_RIGHT);
		return p;
	}

	private static PdfPTable encabezado(Cita cita) {
		PdfPTable t = new PdfPTable(new float[]{3, 2});
		t.setWidthPercentage(100);
		PdfPCell a = base(AZUL, Rectangle.BOTTOM, TURQUESA, 4, 14);
		a.addElement(new Paragraph("Agencia360 · CareCar", f(FontFactory.HELVETICA_BOLD, 20, Color.WHITE)));
		a.addElement(new Paragraph("COMPROBANTE DE CITA", f(FontFactory.HELVETICA, 9, new Color(0xCF, 0xE9, 0xE6))));
		t.addCell(a);
		PdfPCell b = base(AZUL, Rectangle.BOTTOM, TURQUESA, 4, 14);
		b.addElement(der("Folio de cita " + cita.getId(), f(FontFactory.HELVETICA_BOLD, 14, Color.WHITE)));
		boolean ok = cita.getEstado() == EstadoCita.CONFIRMADA;
		b.addElement(der(ok ? "CONFIRMADA" : "CANCELADA",
				f(FontFactory.HELVETICA_BOLD, 10, ok ? new Color(0x7B, 0xF0, 0xB8) : new Color(0xFF, 0xA8, 0x9E))));
		t.addCell(b);
		return t;
	}

	private static PdfPTable cuando(Cita cita) {
		boolean ok = cita.getEstado() == EstadoCita.CONFIRMADA;
		Color color = ok ? VERDE : ROJO;
		PdfPTable t = new PdfPTable(1);
		t.setWidthPercentage(100);
		PdfPCell c = base(FONDO, Rectangle.LEFT, color, 5, 14);
		String dia = cita.getFecha().getDayOfWeek().getDisplayName(TextStyle.FULL, MEXICO);
		String mes = cita.getFecha().getMonth().getDisplayName(TextStyle.FULL, MEXICO);
		c.addElement(new Paragraph(ok ? "TU CITA" : "CITA CANCELADA", f(FontFactory.HELVETICA_BOLD, 9, color)));
		String fecha = Character.toUpperCase(dia.charAt(0)) + dia.substring(1) + " " + cita.getFecha().getDayOfMonth()
				+ " de " + mes + " de " + cita.getFecha().getYear();
		c.addElement(new Paragraph(fecha, f(FontFactory.HELVETICA_BOLD, 20, AZUL)));
		c.addElement(new Paragraph(HORA.format(cita.getHoraInicio()) + " a " + HORA.format(cita.getHoraFin()) + " h",
				f(FontFactory.HELVETICA_BOLD, 26, ok ? AZUL : GRIS)));
		t.addCell(c);
		return t;
	}

	private static PdfPTable vehiculo(VehiculoDeCliente v) {
		PdfPTable t = new PdfPTable(new float[]{5, 6});
		t.setWidthPercentage(100);
		PdfPCell foto = base(FONDO, Rectangle.NO_BORDER, FONDO, 0, 8);
		foto.setVerticalAlignment(Element.ALIGN_MIDDLE);
		Image img = FotosDeAutos.de(v.modelo());
		if (img != null) {
			img.scaleToFit(210, 130);
			img.setAlignment(Element.ALIGN_CENTER);
			foto.addElement(img);
		} else {
			Paragraph p = new Paragraph(v.modelo(), f(FontFactory.HELVETICA_BOLD, 15, AZUL));
			p.setAlignment(Element.ALIGN_CENTER);
			foto.addElement(p);
			foto.setFixedHeight(100);
		}
		t.addCell(foto);
		PdfPCell d = base(FONDO, Rectangle.NO_BORDER, FONDO, 0, 10);
		d.addElement(new Paragraph("TU VEHÍCULO", f(FontFactory.HELVETICA_BOLD, 9, TURQUESA.darker())));
		Paragraph m = new Paragraph((v.marca() != null ? v.marca() + " " : "") + v.modelo(),
				f(FontFactory.HELVETICA_BOLD, 15, AZUL));
		m.setSpacingAfter(6);
		d.addElement(m);
		PdfPTable r = new PdfPTable(2);
		r.setWidthPercentage(100);
		dato(r, "AÑO", String.valueOf(v.anio()));
		dato(r, "VERSIÓN", v.version());
		dato(r, "PLACAS", v.placas());
		dato(r, "VIN", v.vin());
		d.addElement(r);
		t.addCell(d);
		return t;
	}

	private static void dato(PdfPTable r, String etiqueta, String valor) {
		PdfPCell c = new PdfPCell();
		c.setBorder(Rectangle.NO_BORDER);
		c.setPaddingLeft(0);
		c.setPaddingBottom(5);
		c.addElement(new Paragraph(etiqueta, f(FontFactory.HELVETICA, 8, GRIS)));
		c.addElement(
				new Paragraph(valor == null || valor.isBlank() ? "—" : valor, f(FontFactory.HELVETICA_BOLD, 9, AZUL)));
		r.addCell(c);
	}

	private static PdfPTable lugar() {
		PdfPTable t = new PdfPTable(1);
		t.setWidthPercentage(100);
		PdfPCell c = base(Color.WHITE, Rectangle.LEFT, TURQUESA, 4, 10);
		c.addElement(new Paragraph("LUGAR", f(FontFactory.HELVETICA_BOLD, 9, TURQUESA.darker())));
		c.addElement(new Paragraph("Taller de servicio Agencia360 · CareCar", f(FontFactory.HELVETICA_BOLD, 11, AZUL)));
		c.addElement(new Paragraph("Av. Ejemplo 123, Col. Centro, Ciudad de México",
				f(FontFactory.HELVETICA, 10, Color.BLACK)));
		t.addCell(c);
		return t;
	}

	private static PdfPTable recomendaciones() {
		PdfPTable t = new PdfPTable(1);
		t.setWidthPercentage(100);
		PdfPCell c = base(new Color(0xE6, 0xF8, 0xF6), Rectangle.NO_BORDER, TURQUESA, 0, 12);
		c.addElement(new Paragraph("RECOMENDACIONES DE LLEGADA", f(FontFactory.HELVETICA_BOLD, 9, TURQUESA.darker())));
		Font n = f(FontFactory.HELVETICA, 10, Color.BLACK);
		for (String s : new String[]{"Llega 10 minutos antes de tu horario.", "Lleva tu tarjeta de circulación.",
				"Puedes cancelar desde la app hasta 24 horas antes de la cita."}) {
			Paragraph p = new Paragraph("•  " + s, n);
			p.setSpacingBefore(3);
			c.addElement(p);
		}
		t.addCell(c);
		return t;
	}

	private static Image qr(String contenido) {
		try {
			BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
					Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
			BufferedImage imagen = new BufferedImage(matriz.getWidth(), matriz.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < matriz.getWidth(); x++) {
				for (int y = 0; y < matriz.getHeight(); y++) {
					imagen.setRGB(x, y, matriz.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
				}
			}
			Image img = Image.getInstance(imagen, null);
			img.scaleAbsolute(matriz.getWidth() * 4f, matriz.getHeight() * 4f);
			img.setAlignment(Element.ALIGN_RIGHT);
			return img;
		} catch (WriterException | java.io.IOException | com.lowagie.text.BadElementException e) {
			throw new IllegalStateException("No se pudo dibujar el QR de la cita", e);
		}
	}

	private static PdfPTable pie(Cita cita, String contenidoQr) {
		PdfPTable t = new PdfPTable(new float[]{3, 2});
		t.setWidthPercentage(100);
		PdfPCell a = base(Color.WHITE, Rectangle.TOP, FONDO, 1, 8);
		Paragraph c1 = new Paragraph("Agencia360 · CareCar  |  +52 000 000 0000  |  contacto@agencia.example.com",
				f(FontFactory.HELVETICA, 8, GRIS));
		a.addElement(c1);
		a.addElement(new Paragraph("Este comprobante no es un comprobante fiscal.", f(FontFactory.HELVETICA, 8, GRIS)));
		t.addCell(a);
		PdfPCell b = base(Color.WHITE, Rectangle.TOP, FONDO, 1, 8);
		Paragraph p = new Paragraph();
		p.setAlignment(Element.ALIGN_RIGHT);
		p.add(new Chunk(String.format("CITA-%06d", cita.getId()), f(FontFactory.COURIER_BOLD, 14, AZUL)));
		b.addElement(p);
		if (contenidoQr != null) {
			b.addElement(qr(contenidoQr));
			Paragraph leyenda = new Paragraph("Muestra este código en recepción", f(FontFactory.HELVETICA, 8, GRIS));
			leyenda.setAlignment(Element.ALIGN_RIGHT);
			b.addElement(leyenda);
		}
		t.addCell(b);
		return t;
	}
}
