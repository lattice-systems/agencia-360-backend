package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * Datos de entrada que no se aceptan, con un código para que el cliente sepa la
 * causa. Se responde con 400.
 */
public class SolicitudInvalidaException extends RuntimeException {

	private final String codigo;

	public SolicitudInvalidaException(String message, String codigo) {
		super(message);
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}
}
