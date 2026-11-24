package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Tipo de problema de una solicitud de asistencia (V2-M11.01). En el JSON viaja
 * en minúsculas ({@code sin_gasolina}) y se acepta sin importar mayúsculas.
 */
public enum TipoAsistencia {

	LLANTA, BATERIA, CHOQUE, SIN_GASOLINA, OTRO;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el valor no es un tipo conocido (400)
	 */
	@JsonCreator
	public static TipoAsistencia de(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return valueOf(valor.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException desconocido) {
			throw new IllegalArgumentException("El tipo de asistencia no es válido: " + valor);
		}
	}
}
