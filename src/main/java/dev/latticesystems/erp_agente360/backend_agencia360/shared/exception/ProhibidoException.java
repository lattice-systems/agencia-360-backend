package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * La operación no se permite por el estado de la cuenta (por ejemplo, una
 * cuenta desactivada). Se responde con 403 y un código.
 */
public class ProhibidoException extends RuntimeException {

	private final String codigo;

	public ProhibidoException(String message, String codigo) {
		super(message);
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}
}
