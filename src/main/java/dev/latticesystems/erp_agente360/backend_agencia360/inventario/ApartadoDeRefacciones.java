package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Aparta y libera piezas para los pedidos de la app (V2-M14.02). Todo cambio de
 * existencia queda como movimiento de inventario y bloquea la fila de la
 * refacción. Debe llamarse dentro de la transacción que cambia el pedido.
 */
public interface ApartadoDeRefacciones {

	/**
	 * Aparta todas las piezas pedidas o ninguna.
	 *
	 * @param cantidades
	 *            piezas por id de refacción
	 * @return el precio de cada una al apartarla
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si una no existe o no está publicada (404)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException
	 *             con {@code SIN_EXISTENCIA} si no alcanzan (409)
	 */
	List<PiezaApartada> apartar(Long pedidoId, Map<Long, Integer> cantidades);

	/** Devuelve a la existencia las piezas de un pedido cancelado o vencido. */
	void liberar(Long pedidoId, Map<Long, Integer> cantidades);

	record PiezaApartada(Long productoId, int cantidad, BigDecimal precio) {
	}
}
