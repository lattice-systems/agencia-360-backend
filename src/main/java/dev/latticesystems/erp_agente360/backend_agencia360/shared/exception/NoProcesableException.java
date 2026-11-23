package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * La petición está bien formada pero una regla de negocio la rechaza (por
 * ejemplo una dirección fuera de la zona de cobertura). Se responde con 422 y,
 * si trae {@code codigo}, con él (ver {@link CodigosError}).
 */
public class NoProcesableException extends RuntimeException {

	private final String codigo;

	public NoProcesableException(String message, String codigo) {
		super(message);
		this.codigo = codigo;
	}

	/** @return la causa para el cliente, o {@code null} si no hay */
	public String getCodigo() {
		return codigo;
	}
}
