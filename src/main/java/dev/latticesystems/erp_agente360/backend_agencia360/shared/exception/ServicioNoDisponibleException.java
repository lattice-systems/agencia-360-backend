package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * El servicio existe pero este ambiente no lo tiene configurado (por ejemplo,
 * faltan las credenciales de Wallet). Se responde con 503 y su {@code codigo}
 * (ver {@link CodigosError}).
 */
public class ServicioNoDisponibleException extends RuntimeException {

	private final String codigo;

	public ServicioNoDisponibleException(String message, String codigo) {
		super(message);
		this.codigo = codigo;
	}

	/** @return la causa para el cliente */
	public String getCodigo() {
		return codigo;
	}
}
