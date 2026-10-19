package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * Demasiados intentos seguidos. Se responde con 429 y el encabezado
 * {@code Retry-After}.
 */
public class DemasiadosIntentosException extends RuntimeException {

	private final long segundosParaReintentar;

	public DemasiadosIntentosException(String message, long segundosParaReintentar) {
		super(message);
		this.segundosParaReintentar = segundosParaReintentar;
	}

	public long getSegundosParaReintentar() {
		return segundosParaReintentar;
	}
}
