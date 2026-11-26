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

	@Test
	void conOtroLadoMaximoEscalaAEseTamano() throws Exception {
		BufferedImage leida = ImageIO.read(new ByteArrayInputStream(LimpiadorImagen.limpiar(png(3200, 1600), 1600)));

		assertThat(leida.getWidth()).isEqualTo(1600);
		assertThat(leida.getHeight()).isEqualTo(800);
	}

	@Test
	void conLadoMaximoSoloAceptaJpegYPng() throws Exception {
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "gif", gif);
		byte[] webp = "RIFF0000WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

		for (byte[] otro : new byte[][]{gif.toByteArray(), webp, "hola".getBytes(), new byte[0]}) {
			assertThatThrownBy(() -> LimpiadorImagen.limpiar(otro, 1600)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void rechazaPorElEncabezadoMasDeCuarentaMegapixelesSinDecodificar() throws Exception {
		// Un PNG que solo trae el encabezado: si se intentara decodificar fallaría
		// por otra causa.
		byte[] enorme = png(10, 10);
		java.nio.ByteBuffer encabezado = java.nio.ByteBuffer.wrap(enorme);
		encabezado.putInt(16, 10_000).putInt(20, 4_001);
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(enorme, 12, 17);
		encabezado.putInt(29, (int) crc.getValue());

		assertThatThrownBy(() -> LimpiadorImagen.limpiar(enorme, 1600)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("megapíxeles");
	}

	private static byte[] png(int ancho, int alto) throws Exception {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", salida);
		return salida.toByteArray();
	}
}
