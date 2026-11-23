package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/** El proveedor de IA falló, tardó demasiado o se quedó sin cuota. */
public class ProveedorIANoDisponibleException extends RuntimeException {

	public ProveedorIANoDisponibleException(String message) {
		super(message);
	}

	public ProveedorIANoDisponibleException(String message, Throwable causa) {
		super(message, causa);
	}
}
