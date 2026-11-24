package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Deja una foto lista para el modelo sin datos de más (V2-M24.05): la
 * decodifica y la vuelve a escribir como JPEG de máximo 1024 px de lado. Al
 * reescribirla se pierden EXIF, ubicación GPS y miniaturas; todo pasa en
 * memoria.
 */
public final class LimpiadorImagen {

	static final int LADO_MAXIMO = 1024;
	private static final float CALIDAD = 0.85f;

	private LimpiadorImagen() {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si los bytes no son una imagen que Java sepa leer
	 */
	public static byte[] limpiar(byte[] original) {
		BufferedImage leida;
		try {
			leida = ImageIO.read(new ByteArrayInputStream(original));
		} catch (IOException ex) {
			throw new IllegalArgumentException("No es una imagen", ex);
		}
		if (leida == null) {
			throw new IllegalArgumentException("No es una imagen");
		}
		double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(leida.getWidth(), leida.getHeight()));
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
