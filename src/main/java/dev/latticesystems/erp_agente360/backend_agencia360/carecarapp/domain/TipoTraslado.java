package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Sentido de un traslado (V2-M17.02); en el JSON viaja en minúsculas. */
public enum TipoTraslado {

	RECOGER, ENTREGAR;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}
}
