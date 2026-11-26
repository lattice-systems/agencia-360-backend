package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EntregaPedido;
import java.util.List;

/** Lo que el cliente quiere apartar (V2-M14.02). */
public record NuevoPedido(List<Pieza> renglones, EntregaPedido entrega, Long citaId) {

	public record Pieza(Long productoId, int cantidad) {
	}
}
