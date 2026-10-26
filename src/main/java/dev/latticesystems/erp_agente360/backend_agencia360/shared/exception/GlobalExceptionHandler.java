package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejo centralizado de errores para que todos los módulos devuelvan un cuerpo
 * de error consistente en la API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Lo que ve el usuario ante un error no previsto. El detalle técnico va solo al
	 * log: la app CareCar y los frontends muestran el message tal cual.
	 */
	static final String MENSAJE_ERROR_INESPERADO = "Ocurrió un error inesperado. Intenta de nuevo más tarde.";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b).orElse("Datos inválidos");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
				"Bad Request", message, request.getRequestURI(), CodigosError.DATOS_INVALIDOS));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiError.of(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
						"El cuerpo de la petición no es válido", request.getRequestURI(),
						CodigosError.DATOS_INVALIDOS));
	}

	@ExceptionHandler(NoAutenticadoException.class)
	public ResponseEntity<ApiError> handleNoAutenticado(NoAutenticadoException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(HttpStatus.UNAUTHORIZED.value(),
				"Unauthorized", ex.getMessage(), request.getRequestURI(), ex.getCodigo()));
	}

	@ExceptionHandler(ProhibidoException.class)
	public ResponseEntity<ApiError> handleProhibido(ProhibidoException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(HttpStatus.FORBIDDEN.value(), "Forbidden",
				ex.getMessage(), request.getRequestURI(), ex.getCodigo()));
	}

	@ExceptionHandler(SolicitudInvalidaException.class)
	public ResponseEntity<ApiError> handleSolicitudInvalida(SolicitudInvalidaException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
				"Bad Request", ex.getMessage(), request.getRequestURI(), ex.getCodigo()));
	}

	@ExceptionHandler(DemasiadosIntentosException.class)
	public ResponseEntity<ApiError> handleDemasiadosIntentos(DemasiadosIntentosException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosParaReintentar()))
				.body(ApiError.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", ex.getMessage(),
						request.getRequestURI(), CodigosError.DEMASIADOS_INTENTOS));
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ApiError> handleNoEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(ConflictoException.class)
	public ResponseEntity<ApiError> handleConflicto(ConflictoException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleIntegridad(DataIntegrityViolationException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict",
				"El registro entra en conflicto con uno existente", request.getRequestURI()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
				"Bad Request", ex.getMessage(), request.getRequestURI(), CodigosError.DATOS_INVALIDOS));
	}

	/**
	 * Un método HTTP que la ruta no tiene (por ejemplo DELETE donde no se elimina)
	 * es 405, no un error del servidor.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMetodoNoPermitido(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		String mensaje = "El método " + ex.getMethod() + " no está permitido en esta ruta";
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError
				.of(HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed", mensaje, request.getRequestURI()));
	}

	/**
	 * Una ruta que no existe (por ejemplo, una que se quitó) es 404, no un error
	 * del servidor.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleRutaNoEncontrada(NoResourceFoundException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found",
				"No existe la ruta " + request.getRequestURI(), request.getRequestURI()));
	}

	/**
	 * Si la respuesta ya se envió (por ejemplo, una conexión en tiempo real que el
	 * navegador cerró), ya no se puede responder con un error: solo se registra.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request,
			HttpServletResponse response) {
		if (response.isCommitted()) {
			LOG.debug("La respuesta de {} ya se había enviado: {}", request.getRequestURI(), ex.toString());
			return null;
		}
		LOG.error("Error inesperado al atender la petición", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
						MENSAJE_ERROR_INESPERADO, request.getRequestURI()));
	}
}
