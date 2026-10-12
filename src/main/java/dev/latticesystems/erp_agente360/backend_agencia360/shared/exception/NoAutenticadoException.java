package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/** Credenciales inválidas o sesión no iniciada. Se responde con 401. */
public class NoAutenticadoException extends RuntimeException {

	public NoAutenticadoException(String message) {
		super(message);
	}
}
