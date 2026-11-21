package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * La operación choca con el estado actual de los datos. Se responde con 409 y,
 * si trae {@code codigo}, con él (ver {@link CodigosError}).
 */
public class ConflictoException extends RuntimeException {

	private final String codigo;

	public ConflictoException(String message) {
		this(message, null);
	}

	public ConflictoException(String message, String codigo) {
		super(message);
		this.codigo = codigo;
	}

	/** @return la causa para el cliente, o {@code null} si no hay */
	public String getCodigo() {
		return codigo;
	}
}
