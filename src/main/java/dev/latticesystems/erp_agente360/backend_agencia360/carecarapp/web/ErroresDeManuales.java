package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ApiError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Un manual que pasa del límite de subida responde 413 con el formato de error
 * de la API, en lugar de caer en el error inesperado. Va antes del manejador
 * general porque ese atrapa cualquier excepción.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ErroresDeManuales {

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> handleDemasiadoGrande(MaxUploadSizeExceededException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large",
						"El manual debe ser un PDF de hasta 20 MB", request.getRequestURI(),
						CodigosError.DATOS_INVALIDOS));
	}
}
