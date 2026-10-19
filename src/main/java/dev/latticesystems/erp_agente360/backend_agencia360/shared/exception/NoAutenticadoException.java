package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/** Credenciales inválidas o sesión no iniciada. Se responde con 401. */
public class NoAutenticadoException extends RuntimeException {

	private final String codigo;

	public NoAutenticadoException(String message) {
		this(message, null);
	}

	public NoAutenticadoException(String message, String codigo) {
		super(message);
		this.codigo = codigo;
	}

	/** Valor de {@link ApiError#codigo()}, o {@code null}. */
	public String getCodigo() {
		return codigo;
	}
}
