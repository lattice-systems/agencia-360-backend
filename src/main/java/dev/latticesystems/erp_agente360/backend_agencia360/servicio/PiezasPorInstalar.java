package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

import java.util.List;

/**
 * Piezas que el cliente apartó para que se instalen en la cita de una orden
 * (V2-M14.03). La implementa el módulo de la app: así Servicio no depende de
 * ella y el taller ve el pedido en la orden.
 */
public interface PiezasPorInstalar {

	/** Los pedidos no cancelados de esa cita; vacío si {@code citaId} es nulo. */
	List<PedidoPorInstalar> deCita(Long citaId);

	record PedidoPorInstalar(Long pedidoId, String estado, List<Pieza> piezas) {
	}

	record Pieza(String codigo, String nombre, int cantidad) {
	}
}
