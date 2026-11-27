package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import java.util.Map;

/**
 * La bandeja de avisos del cliente ({@code notificacion}). Aquí solo se
 * escribe; la lectura paginada es de V2-M06.04.
 */
public interface Bandeja {

	/**
	 * Guarda el aviso en su propia transacción: se llama después de confirmar la
	 * que lo causó, que ya no admite escrituras.
	 *
	 * @param datos
	 *            solo ids del recurso, nunca datos personales
	 */
	void guardar(Long clienteId, ContenidoAviso contenido, String evento, Map<String, String> datos);
}
