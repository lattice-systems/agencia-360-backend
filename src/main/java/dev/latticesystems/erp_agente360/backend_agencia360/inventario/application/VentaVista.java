package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVenta;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Venta;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Venta de una unidad (ERP-1.0). Los datos de la unidad vienen vacíos si el
 * prospecto se finalizó sin que Facturación la eligiera.
 *
 * @param precio
 *            precio de la unidad cuando se eligió
 * @param registradaEn
 *            cuando el prospecto llegó a Finalizado; vacío mientras tanto
 */
public record VentaVista(Long id, Long prospectoId, Long vehiculoId, String vin, String modelo, String version,
		Integer anio, String color, BigDecimal precio, EstatusVenta estatus, Instant asignadaEn, Instant registradaEn) {

	static VentaVista de(Venta venta) {
		Vehiculo unidad = venta.getVehiculo();
		return new VentaVista(venta.getId(), venta.getProspectoId(), unidad == null ? null : unidad.getId(),
				unidad == null ? null : unidad.getVin(), unidad == null ? null : unidad.getModelo(),
				unidad == null ? null : unidad.getVersion(), unidad == null ? null : unidad.getAnio(),
				unidad == null ? null : unidad.getColor(), venta.getPrecio(), venta.getEstatus(), venta.getAsignadaEn(),
				venta.getRegistradaEn());
	}
}
