package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Envío de correos de la app. En desarrollo llegan a Mailpit; el servicio de
 * producción en Azure está por definir.
 */
public interface EnvioCorreos {

	/**
	 * Manda el enlace de recuperación de un solo uso y, para la app v1, su código
	 * (V2-T05.04). Un fallo de envío no se propaga: la respuesta de la API es la
	 * misma exista o no el correo.
	 */
	void enviarRecuperacion(String correo, String nombre, String enlace, String codigo);

	/**
	 * Manda la bienvenida con la contraseña temporal de la cuenta nueva (CRM-1.3).
	 * El cliente la cambia en su primer ingreso.
	 */
	void enviarCuentaNueva(String correo, String nombre, String contrasenaTemporal);
}
