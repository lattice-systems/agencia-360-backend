package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.Servicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * "Ver detalles" de un servicio: sus renglones como el ticket de una tienda.
 * Los precios traen el IVA incluido; el subtotal y el IVA se desglosan del
 * total.
 *
 * @param atendidoPor
 *            nombre del usuario de Servicio que lo registró
 */
public record ServicioDetalle(Long id, Long vehiculoId, LocalDate fecha, int kilometraje, String atendidoPor,
		List<Renglon> renglones, BigDecimal subtotal, BigDecimal iva, BigDecimal total) {

	/** Un renglón del servicio; {@code importe} es cantidad por precio unitario. */
	public record Renglon(TipoRenglon tipo, String concepto, int cantidad, BigDecimal precioUnitario,
			BigDecimal importe) {

		static Renglon de(RenglonServicio renglon) {
			return new Renglon(renglon.getTipo(), renglon.getConcepto(), renglon.getCantidad(),
					renglon.getPrecioUnitario(), renglon.getImporte());
		}
	}

	static ServicioDetalle de(Servicio servicio, String atendidoPor) {
		return new ServicioDetalle(servicio.getId(), servicio.getVehiculoClienteId(), servicio.getFecha(),
				servicio.getKilometraje(), atendidoPor, servicio.getRenglones().stream().map(Renglon::de).toList(),
				servicio.getSubtotal(), servicio.getIva(), servicio.getTotal());
	}
}
