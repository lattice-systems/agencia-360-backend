package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Estado de un traslado. Nace {@code programado}; el resto de los estados los
 * mueve V2-M17.03. En el JSON viaja en minúsculas.
 */
public enum EstadoTraslado {

	PROGRAMADO, EN_CAMINO, RECOGIDO, EN_TALLER, DE_REGRESO, ENTREGADO, CANCELADO;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}
}
