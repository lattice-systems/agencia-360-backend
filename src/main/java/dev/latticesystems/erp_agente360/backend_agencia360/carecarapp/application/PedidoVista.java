package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Un pedido como lo pide el contrato (V2-M14.05). El personal recibe además al
 * cliente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoVista(Long id, String estado, String entrega, BigDecimal total, Instant creadoEn, Instant venceEn,
		Instant listoEn, Instant entregadoEn, Instant canceladoEn, String motivoCancelacion, CitaDePedido cita,
		List<Renglon> renglones, Long clienteId) {

	public record CitaDePedido(Long id, LocalDate fecha, String hora, Long vehiculoId) {
	}

	public record Renglon(Long productoId, String codigo, String nombre, int cantidad, BigDecimal precio,
			BigDecimal importe) {
	}
}
