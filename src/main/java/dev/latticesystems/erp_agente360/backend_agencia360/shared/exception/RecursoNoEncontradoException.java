package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * El recurso no existe o no está al alcance de quien lo pide. Se responde con
 * 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String message) {
		super(message);
	}
}
