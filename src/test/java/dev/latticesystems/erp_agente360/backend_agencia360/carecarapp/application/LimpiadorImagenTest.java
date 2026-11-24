package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LimpiadorImagenTest {

	@Test
	void devuelveUnJpegSinMetadatosDeMaximo1024Px() throws Exception {
		byte[] limpia = LimpiadorImagen.limpiar(png(3000, 1500));

		assertThat(limpia[0] & 0xFF).isEqualTo(0xFF);
		assertThat(limpia[1] & 0xFF).isEqualTo(0xD8);
		BufferedImage leida = ImageIO.read(new ByteArrayInputStream(limpia));
		assertThat(leida.getWidth()).isEqualTo(1024);
		assertThat(leida.getHeight()).isEqualTo(512);
		// Sin segmento APP1 (Exif): solo el JFIF que escribe ImageIO.
		assertThat(new String(limpia, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void unaImagenChicaConservaSuTamano() throws Exception {
		BufferedImage leida = ImageIO.read(new ByteArrayInputStream(LimpiadorImagen.limpiar(png(40, 30))));
		assertThat(leida.getWidth()).isEqualTo(40);
	}

	@Test
	void algoQueNoEsImagenSeRechaza() {
		assertThatThrownBy(() -> LimpiadorImagen.limpiar("hola".getBytes()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static byte[] png(int ancho, int alto) throws Exception {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", salida);
		return salida.toByteArray();
	}
}
