package dev.latticesystems.erp_agente360.backend_agencia360.shared.pdf;

import com.lowagie.text.Image;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Foto de un modelo desde el classpath ({@code /fotos-autos/<clave>.jpg}) para los PDF. */
public final class FotosDeAutos {

	private static final Set<String> MODELOS = Set.of("march", "versa", "kicks", "sentra", "xtrail", "frontier",
			"leaf", "ariya", "z", "gtr", "altima", "pathfinder", "murano", "armada", "titan", "urvan", "maxima",
			"juke", "qashqai", "patrol", "tiida", "note");

	private FotosDeAutos() {
	}

	/** La foto del modelo («X-Trail» es xtrail) o {@code null} si no hay. */
	public static Image de(String modelo) {
		if (modelo == null) {
			return null;
		}
		String clave = Normalizer.normalize(modelo, Normalizer.Form.NFD).toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]", "");
		if (!MODELOS.contains(clave)) {
			return null;
		}
		try (InputStream in = FotosDeAutos.class.getResourceAsStream("/fotos-autos/" + clave + ".jpg")) {
			return in == null ? null : Image.getInstance(in.readAllBytes());
		} catch (Exception e) {
			return null;
		}
	}
}
