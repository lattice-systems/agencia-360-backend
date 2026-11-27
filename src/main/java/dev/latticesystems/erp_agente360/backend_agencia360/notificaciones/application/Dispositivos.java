package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import java.util.List;

/**
 * Los dispositivos que reciben push ({@code dispositivo_push}). Lo implementa
 * la capa de infraestructura con JDBC.
 */
public interface Dispositivos {

	/**
	 * Registra o actualiza la instalación a nombre de la cuenta del cliente. Si la
	 * instalación era de otra cuenta pasa a esta; si el token ya estaba en otra
	 * instalación, esa fila se descarta.
	 *
	 * @return {@code false} si el cliente no tiene cuenta de la app
	 */
	boolean registrar(Long clienteId, String instalacionId, String tokenFcm, String plataforma, String idioma);

	/**
	 * Borra la instalación solo si es de este cliente. Idempotente.
	 */
	void darDeBaja(Long clienteId, String instalacionId);

	/** Los dispositivos activos de las cuentas del cliente. */
	List<DispositivoDeCliente> activosDe(Long clienteId);

	/** Marca el dispositivo como inactivo (FCM dijo que su token ya no existe). */
	void desactivar(Long dispositivoId);

	record DispositivoDeCliente(Long id, String tokenFcm, String idioma) {
	}
}
