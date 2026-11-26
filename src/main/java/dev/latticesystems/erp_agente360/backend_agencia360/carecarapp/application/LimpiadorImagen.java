package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Deja una foto sin datos de más (V2-M24.05 y V2-M26.02): la decodifica y la
 * vuelve a escribir como JPEG. Al reescribirla se pierden EXIF, ubicación GPS y
 * miniaturas; todo pasa en memoria.
 */
public final class LimpiadorImagen {

	static final int LADO_MAXIMO = 1024;
	private static final long PIXELES_MAXIMOS = 40_000_000L;
	private static final float CALIDAD = 0.85f;

	private static final Set<String> FORMATOS = Set.of("jpeg", "png");

	private LimpiadorImagen() {
	}

	/**
	 * La foto lista para el modelo, de máximo 1024 px de lado.
	 *
	 * @throws IllegalArgumentException
	 *             si los bytes no son una imagen que Java sepa leer
	 */
	public static byte[] limpiar(byte[] original) {
		return reescribir(original, LADO_MAXIMO);
	}

	/**
	 * Como {@link #limpiar(byte[])} con otro lado máximo, solo para JPEG o PNG.
	 * Antes de decodificar mira el tamaño en el encabezado, para que un archivo
	 * chico no agote la memoria.
	 *
	 * @throws IllegalArgumentException
	 *             si no es un JPEG o PNG legible o pasa de 40 megapíxeles
	 */
	public static byte[] limpiar(byte[] original, int ladoMaximo) {
		revisarEncabezado(original);
		return reescribir(original, ladoMaximo);
	}

	private static void revisarEncabezado(byte[] original) {
		try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
			Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
			if (!lectores.hasNext()) {
				throw new IllegalArgumentException("La foto debe ser JPEG o PNG");
			}
			ImageReader lector = lectores.next();
			try {
				if (!FORMATOS.contains(lector.getFormatName().toLowerCase(Locale.ROOT))) {
					throw new IllegalArgumentException("La foto debe ser JPEG o PNG");
				}
				lector.setInput(entrada);
				if ((long) lector.getWidth(0) * lector.getHeight(0) > PIXELES_MAXIMOS) {
					throw new IllegalArgumentException("La foto pasa de 40 megapíxeles");
				}
			} finally {
				lector.dispose();
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException("No es una imagen", ex);
		}
	}

	private static byte[] reescribir(byte[] original, int ladoMaximo) {
		BufferedImage leida;
		try {
			leida = ImageIO.read(new ByteArrayInputStream(original));
		} catch (IOException ex) {
			throw new IllegalArgumentException("No es una imagen", ex);
		}
		if (leida == null) {
			throw new IllegalArgumentException("No es una imagen");
		}
		double escala = Math.min(1.0, (double) ladoMaximo / Math.max(leida.getWidth(), leida.getHeight()));
		int ancho = Math.max(1, (int) Math.round(leida.getWidth() * escala));
		int alto = Math.max(1, (int) Math.round(leida.getHeight() * escala));
		BufferedImage limpia = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D lienzo = limpia.createGraphics();
		lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		lienzo.drawImage(leida, 0, 0, ancho, alto, null);
		lienzo.dispose();

		ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam parametros = escritor.getDefaultWriteParam();
		parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		parametros.setCompressionQuality(CALIDAD);
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ImageOutputStream flujo = ImageIO.createImageOutputStream(salida)) {
			escritor.setOutput(flujo);
			escritor.write(null, new IIOImage(limpia, null, null), parametros);
		} catch (IOException ex) {
			throw new IllegalStateException("No se pudo escribir la imagen", ex);
		} finally {
			escritor.dispose();
		}
		return salida.toByteArray();
	}
}
