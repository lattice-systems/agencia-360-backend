package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Estado de una solicitud de asistencia: nace {@code recibida}, pasa a
 * {@code en_camino} y termina {@code atendida} o {@code cancelada} (V2-M11.04).
 */
public enum EstadoAsistencia {

	RECIBIDA, EN_CAMINO, ATENDIDA, CANCELADA;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Una solicitud abierta todavía necesita atención. */
	public boolean abierta() {
		return this == RECIBIDA || this == EN_CAMINO;
	}

	/** Si el personal puede mover la solicitud de este estado al {@code otro}. */
	public boolean puedePasarA(EstadoAsistencia otro) {
		return switch (this) {
			case RECIBIDA -> otro == EN_CAMINO || otro == CANCELADA;
			case EN_CAMINO -> otro == ATENDIDA || otro == CANCELADA;
			case ATENDIDA, CANCELADA -> false;
		};
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el texto no es un estado conocido (400)
	 */
	public static EstadoAsistencia desde(String texto) {
		for (EstadoAsistencia estado : values()) {
			if (estado.valor().equals(texto)) {
				return estado;
			}
		}
		throw new IllegalArgumentException("El estado debe ser recibida, en_camino, atendida o cancelada");
	}
}
