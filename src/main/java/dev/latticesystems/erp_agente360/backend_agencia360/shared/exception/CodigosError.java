package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * Valores del campo {@code codigo} de {@link ApiError} que comparte la API
 * (contrato de la app CareCar).
 */
public final class CodigosError {

	public static final String CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS";
	public static final String CUENTA_DESACTIVADA = "CUENTA_DESACTIVADA";
	public static final String DEMASIADOS_INTENTOS = "DEMASIADOS_INTENTOS";
	public static final String CODIGO_INVALIDO = "CODIGO_INVALIDO";
	public static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
	public static final String FRANJA_SIN_CUPO = "FRANJA_SIN_CUPO";
	public static final String FUERA_DE_PLAZO = "FUERA_DE_PLAZO";
	public static final String CITA_NO_CANCELABLE = "CITA_NO_CANCELABLE";
	public static final String CITA_DUPLICADA = "CITA_DUPLICADA";
	public static final String CITA_NO_REPROGRAMABLE = "CITA_NO_REPROGRAMABLE";

	private CodigosError() {
	}
}
