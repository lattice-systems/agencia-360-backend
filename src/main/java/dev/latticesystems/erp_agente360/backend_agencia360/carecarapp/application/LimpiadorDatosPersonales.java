package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Quita de una pregunta lo que parezca un dato personal antes de mandarla al
 * proveedor de IA (ADR 0007): correo, teléfono, VIN y placas. El cliente puede
 * escribirlos sin querer, y el nivel gratis del proveedor usa lo que recibe.
 */
public final class LimpiadorDatosPersonales {

	static final String OMITIDO = "[dato omitido]";

	private static final List<Pattern> PATRONES = List.of(Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+"),
			// VIN: 17 caracteres sin I, O ni Q.
			Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b", Pattern.CASE_INSENSITIVE),
			// Teléfono: 10 dígitos o más, con espacios, guiones o lada.
			Pattern.compile("(\\+?\\d[\\d\\s().-]{8,}\\d)"),
			// Placas mexicanas: ABC-123-D, ABC-12-34, 123-ABC, etc.
			Pattern.compile("\\b[A-Z]{2,3}-?\\d{2,4}-?[A-Z0-9]{0,2}\\b", Pattern.CASE_INSENSITIVE));

	private LimpiadorDatosPersonales() {
	}

	public static String limpiar(String texto) {
		String limpio = texto;
		for (Pattern patron : PATRONES) {
			limpio = patron.matcher(limpio).replaceAll(OMITIDO);
		}
		return limpio;
	}
}
