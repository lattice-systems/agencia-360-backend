package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Estado de una solicitud de asistencia: nace {@code recibida}, pasa a
 * {@code en_camino} y termina {@code atendida} o {@code cancelada}. Los cambios
 * de estado llegan con V2-M11.04.
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
}
