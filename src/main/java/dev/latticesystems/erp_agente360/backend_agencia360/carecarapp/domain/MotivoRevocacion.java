package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Por qué se revocó una sesión de la app.
 *
 * <p>
 * No es un dato de auditoría: <strong>decide si reusar ese refresh token se
 * toma como posible robo</strong> y cierra todas las sesiones de la cuenta.
 * Solo {@link #REFRESCO} es señal de robo, porque es el único caso en el que el
 * token siguió existiendo después de usarse: si llega otra vez, alguien tiene
 * una copia.
 */
public enum MotivoRevocacion {

	/**
	 * La consumió un refresco: se cambió por un par nuevo. Reusarla <b>es</b> señal
	 * de robo.
	 */
	REFRESCO,

	/**
	 * La cerró una persona: cerrar sesión, cerrar esa sesión desde «mis sesiones»
	 * (V2-M01.09), cerrar todas, desactivar la cuenta o restablecer la contraseña.
	 *
	 * <p>
	 * Reusarla <b>no</b> es señal de robo, y tratarla como tal sería peor que
	 * inútil: el dispositivo que acaban de cerrar todavía tiene su token y lo va a
	 * usar en su siguiente intento, así que cerrar una sesión dejaría al cliente
	 * fuera de todas sus demás sesiones, incluida la del teléfono desde el que lo
	 * pidió. Con un celular robado le regalaría al ladrón una negación de servicio.
	 */
	CIERRE
}
