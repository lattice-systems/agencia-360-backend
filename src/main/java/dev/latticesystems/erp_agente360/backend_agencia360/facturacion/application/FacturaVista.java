package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.Factura;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Factura emitida (ERP-1.3), sin el PDF: se descarga aparte. {@code modelo} es
 * el Tipo (Sentra) y {@code anio} el Modelo (2026), como en la factura.
 *
 * @param cliente
 *            nombre del receptor como quedó en la factura
 */
public record FacturaVista(Long id, String folio, Long prospectoId, String cliente, String rfc, Long vehiculoId,
		String vin, String marca, String modelo, String version, Integer anio, String color, BigDecimal subtotal,
		BigDecimal iva, BigDecimal total, Instant emitidaEn) {

	static FacturaVista de(Factura factura) {
		return new FacturaVista(factura.getId(), factura.getFolio(), factura.getProspectoId(),
				factura.getReceptorNombre(), factura.getReceptorRfc(), factura.getVehiculoId(), factura.getVin(),
				factura.getMarca(), factura.getModelo(), factura.getVersion(), factura.getAnio(), factura.getColor(),
				factura.getSubtotal(), factura.getIva(), factura.getTotal(), factura.getEmitidaEn());
	}
}
