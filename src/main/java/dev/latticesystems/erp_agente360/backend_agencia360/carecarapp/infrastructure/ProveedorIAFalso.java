package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProveedorIA;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proveedor sin red ni llave para pruebas y para el backend local sin
 * {@code GEMINI_API_KEY} (ADR 0007). No calcula vectores (la búsqueda cae al
 * texto completo) y responde citando la primera página que recibió.
 */
public class ProveedorIAFalso implements ProveedorIA {

	private static final Pattern PAGINA = Pattern.compile("\\[Página (\\d+)]\\n([^\\[]*)");
	private static final int LARGO_EXTRACTO = 160;

	@Override
	public String nombre() {
		return "falso";
	}

	@Override
	public Optional<float[]> embeber(String texto) {
		return Optional.empty();
	}

	@Override
	public RespuestaIA responder(String instrucciones, String contenido) {
		Matcher primera = PAGINA.matcher(contenido);
		if (!primera.find()) {
			return new RespuestaIA("No encontré eso en tu manual.", false, List.of());
		}
		int pagina = Integer.parseInt(primera.group(1));
		String extracto = primera.group(2).strip().replaceAll("\\s+", " ");
		if (extracto.length() > LARGO_EXTRACTO) {
			extracto = extracto.substring(0, LARGO_EXTRACTO) + "…";
		}
		return new RespuestaIA("Según tu manual (página " + pagina + "): " + extracto, true, List.of(pagina));
	}

	/**
	 * Decide por el color dominante: rojo es presión de aceite (roja), ámbar es
	 * check engine (amarilla) y lo demás no se reconoce.
	 */
	@Override
	public RespuestaTestigo identificarTestigo(String instrucciones, byte[] jpeg) {
		java.awt.image.BufferedImage foto;
		try {
			foto = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
		} catch (java.io.IOException ex) {
			return new RespuestaTestigo(false, null, null, null);
		}
		long rojo = 0;
		long verde = 0;
		long azul = 0;
		int pixeles = foto.getWidth() * foto.getHeight();
		for (int x = 0; x < foto.getWidth(); x++) {
			for (int y = 0; y < foto.getHeight(); y++) {
				int rgb = foto.getRGB(x, y);
				rojo += (rgb >> 16) & 0xFF;
				verde += (rgb >> 8) & 0xFF;
				azul += rgb & 0xFF;
			}
		}
		rojo /= pixeles;
		verde /= pixeles;
		azul /= pixeles;
		if (rojo > 150 && verde < 90 && azul < 90) {
			return new RespuestaTestigo(true, "Presión de aceite", "roja",
					"Detente en un lugar seguro, apaga el motor y revisa el nivel de aceite. "
							+ "No sigas manejando si sigue encendida.");
		}
		if (rojo > 180 && verde > 110 && azul < 80) {
			return new RespuestaTestigo(true, "Check engine", "amarilla",
					"Puedes seguir con precaución, pero agenda una revisión pronto.");
		}
		return new RespuestaTestigo(false, null, null, null);
	}
}
