package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones;

import java.util.Map;

/**
 * Puerta de entrada del módulo: algo le pasó a un cliente y hay que avisarle
 * por push y dejarlo en su bandeja. No lanza nunca: un fallo del canal solo se
 * registra, para no revertir la transacción que causó el aviso.
 */
public interface EntregaDeAvisos {

	/**
	 * @param clienteId
	 *            a quién se le avisa
	 * @param evento
	 *            nombre del evento de dominio, por ejemplo
	 *            {@code mantenimiento.recordatorio}; uno sin texto definido no
	 *            genera aviso
	 * @param datos
	 *            los datos del evento; de aquí salen el texto y el destino
	 */
	void avisar(Long clienteId, String evento, Map<String, Object> datos);
}
