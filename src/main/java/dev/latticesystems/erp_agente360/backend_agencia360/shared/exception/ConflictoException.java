package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * La operación choca con el estado actual de los datos. Se responde con 409.
 */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String message) {
		super(message);
	}
}
