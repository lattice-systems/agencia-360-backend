package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Quién dio una lectura de kilometraje (V2-M05.03). El taller es la fuente
 * oficial y el cliente nunca la sobrescribe. En el JSON viaja en minúsculas.
 */
public enum FuenteLectura {

	TALLER, CLIENTE;

	@JsonValue
	public String valor() {
		return name().toLowerCase(Locale.ROOT);
	}
}
