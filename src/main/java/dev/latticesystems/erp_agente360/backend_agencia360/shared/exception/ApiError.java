package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo de error de toda la API.
 *
 * @param codigo
 *            causa del error para que el cliente decida sin leer el texto (por
 *            ejemplo {@code CUENTA_DESACTIVADA}); no se envía si no hay
 * @param campos
 *            qué está mal en cada campo de la petición (por ejemplo
 *            {@code rfc → "El dígito verificador del RFC no coincide"}); no se
 *            envía si no hay
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
		@JsonInclude(JsonInclude.Include.NON_NULL) String codigo,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> campos) {

	public static ApiError of(int status, String error, String message, String path) {
		return of(status, error, message, path, null);
	}

	public static ApiError of(int status, String error, String message, String path, String codigo) {
		return new ApiError(Instant.now(), status, error, message, path, codigo, Map.of());
	}

	public static ApiError conCampos(int status, String error, String message, String path, String codigo,
			Map<String, String> campos) {
		return new ApiError(Instant.now(), status, error, message, path, codigo, Map.copyOf(campos));
	}
}
