package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Cuerpo de error de toda la API.
 *
 * @param codigo
 *            causa del error para que el cliente decida sin leer el texto (por
 *            ejemplo {@code CUENTA_DESACTIVADA}); no se envía si no hay
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
		@JsonInclude(JsonInclude.Include.NON_NULL) String codigo) {

	public static ApiError of(int status, String error, String message, String path) {
		return of(status, error, message, path, null);
	}

	public static ApiError of(int status, String error, String message, String path, String codigo) {
		return new ApiError(Instant.now(), status, error, message, path, codigo);
	}
}
