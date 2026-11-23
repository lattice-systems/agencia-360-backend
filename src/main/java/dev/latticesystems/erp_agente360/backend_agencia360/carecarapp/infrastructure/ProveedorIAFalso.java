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
}
