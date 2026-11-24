package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Tipo de gasto de la bitácora del conductor (V2-M18). En el JSON viaja en
 * minúsculas ({@code gasolina}) y se acepta sin importar mayúsculas.
 */
public enum TipoGasto {

	GASOLINA, ESTACIONAMIENTO, CASETAS, LAVADO, OTRO;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el valor no es un tipo conocido (400)
	 */
	@JsonCreator
	public static TipoGasto de(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return valueOf(valor.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException desconocido) {
			throw new IllegalArgumentException("El tipo de gasto no es válido: " + valor);
		}
	}
}
