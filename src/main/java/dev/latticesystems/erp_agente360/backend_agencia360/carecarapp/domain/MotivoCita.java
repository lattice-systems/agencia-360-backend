package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Motivo por el que el cliente agenda una cita (V2-M02.08). En el JSON viaja en
 * minúsculas ({@code mantenimiento}) y se acepta sin importar mayúsculas.
 */
public enum MotivoCita {

	MANTENIMIENTO, REPARACION, REVISION, GARANTIA, CAMPANA, OTRO;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el valor no es un motivo conocido (400)
	 */
	@JsonCreator
	public static MotivoCita de(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return valueOf(valor.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException desconocido) {
			throw new IllegalArgumentException("El motivo no es válido: " + valor);
		}
	}
}
