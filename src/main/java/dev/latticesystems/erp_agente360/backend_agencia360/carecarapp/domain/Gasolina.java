package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Nivel de gasolina que el chofer anota en el checklist del traslado
 * (V2-M17.04). En la base va en mayúsculas, como en la ficha, y en el JSON en
 * minúsculas.
 */
public enum Gasolina {

	VACIO, UN_CUARTO, MEDIO, TRES_CUARTOS, LLENO;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el texto no es un nivel conocido (400)
	 */
	public static Gasolina desde(String texto) {
		for (Gasolina nivel : values()) {
			if (nivel.valor().equals(texto)) {
				return nivel;
			}
		}
		throw new IllegalArgumentException("La gasolina debe ser vacio, un_cuarto, medio, tres_cuartos o lleno");
	}
}
