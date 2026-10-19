package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Envío de correos de la app. En desarrollo llegan a Mailpit; el servicio de
 * producción en Azure está por definir.
 */
public interface EnvioCorreos {

	/**
	 * Manda el código de recuperación. Un fallo de envío no se propaga: la
	 * respuesta de la API es la misma exista o no el correo.
	 */
	void enviarCodigoRecuperacion(String correo, String nombre, String codigo);
}
