package dev.latticesystems.erp_agente360.backend_agencia360.shared.exception;

/**
 * La agencia todavía no tiene parámetros para valuar ese auto. No es un fallo
 * de quien pregunta ni del sistema: simplemente falta valuar esa marca, modelo
 * y año. Se responde con 422 y el código
 * {@link CodigosError#SIN_PARAMETROS_DE_VALUACION}.
 */
public class SinParametrosDeValuacionException extends RuntimeException {

	public SinParametrosDeValuacionException(String message) {
		super(message);
	}
}
