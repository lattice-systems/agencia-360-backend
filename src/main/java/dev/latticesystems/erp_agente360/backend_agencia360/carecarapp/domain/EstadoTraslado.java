package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Estado de un traslado. Nace {@code programado}; el chofer lo mueve
 * (V2-M17.03) y solo el sistema lo cancela. En el JSON viaja en minúsculas.
 */
public enum EstadoTraslado {

	PROGRAMADO, EN_CAMINO, RECOGIDO, EN_TALLER, DE_REGRESO, ENTREGADO, CANCELADO;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** El chofer comparte su ubicación solo en estos estados. */
	public boolean compartePosicion() {
		return this == EN_CAMINO || this == DE_REGRESO;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el texto no es un estado conocido (400)
	 */
	public static EstadoTraslado desde(String texto) {
		for (EstadoTraslado estado : values()) {
			if (estado.valor().equals(texto)) {
				return estado;
			}
		}
		throw new IllegalArgumentException(
				"El estado debe ser programado, en_camino, recogido, en_taller, de_regreso, entregado o cancelado");
	}
}
