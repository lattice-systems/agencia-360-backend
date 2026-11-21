package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manda notificaciones push a los teléfonos (APP-6). La implementación de
 * Firebase solo existe si hay credenciales; sin ellas, la de solo registro.
 */
public interface EnvioPush {

	/**
	 * @return los tokens que Firebase ya no reconoce, para borrarlos
	 */
	Set<String> enviar(List<String> tokens, Notificacion notificacion);

	/**
	 * @param datos
	 *            pares que la app lee al tocar la notificación, por ejemplo
	 *            {@code tipo=CITA} y {@code citaId=12}
	 */
	record Notificacion(String titulo, String cuerpo, Map<String, String> datos) {
	}
}
