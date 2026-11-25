package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Map;
import java.util.Objects;

/**
 * Algo que le pasó a un cliente y la app debe saber al instante (por ejemplo,
 * cambió el estado de su asistencia o de su traslado). Se publica dentro de la
 * transacción que lo causa; los avisos en vivo lo mandan por SSE al cliente
 * dueño y el push (M06) podrá escuchar el mismo evento.
 *
 * @param evento
 *            nombre del evento SSE, por ejemplo {@code asistencia.estado}
 * @param datos
 *            lo que viaja como JSON en el aviso
 */
public record AvisoAlCliente(Long clienteId, String evento, Map<String, Object> datos) {

	public AvisoAlCliente {
		Objects.requireNonNull(clienteId, "clienteId");
		Objects.requireNonNull(evento, "evento");
		datos = Map.copyOf(datos);
	}
}
