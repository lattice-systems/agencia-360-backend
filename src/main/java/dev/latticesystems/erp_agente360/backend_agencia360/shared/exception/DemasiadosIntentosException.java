package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * Demasiados intentos seguidos. Se responde con 429 y el encabezado
 * {@code Retry-After}.
 */
public class DemasiadosIntentosException extends RuntimeException {

	private final long segundosParaReintentar;
	private final String codigo;

	public DemasiadosIntentosException(String message, long segundosParaReintentar) {
		this(message, segundosParaReintentar, CodigosError.DEMASIADOS_INTENTOS);
	}

	public DemasiadosIntentosException(String message, long segundosParaReintentar, String codigo) {
		super(message);
		this.segundosParaReintentar = segundosParaReintentar;
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}

	public long getSegundosParaReintentar() {
		return segundosParaReintentar;
	}
}
