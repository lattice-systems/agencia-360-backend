package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Cuenta de la app de un cliente, como la ve el Gerente de marketing.
 *
 * @param estado
 *            SIN_CUENTA, ACTIVA o DESACTIVADA
 * @param debeCambiarContrasena
 *            {@code true} si el cliente aún no entra por primera vez
 */
public record EstadoCuentaApp(Long clienteId, String estado, boolean debeCambiarContrasena) {

	public static final String SIN_CUENTA = "SIN_CUENTA";
	public static final String ACTIVA = "ACTIVA";
	public static final String DESACTIVADA = "DESACTIVADA";
}
