package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.EstadoLiquidacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Pagos de un prospecto en Pago, como los ven Caja y Contabilidad (ERP-1.1).
 *
 * @param vendedorNombre
 *            nulo si el vendedor ya no existe
 * @param estado
 *            {@code ABIERTA} también cuando aún no hay ningún pago
 * @param total
 *            suma de los pagos, sin los anulados
 * @param enviadaEn
 *            cuándo Caja los envió a Contabilidad por última vez
 * @param motivoRechazo
 *            solo si Contabilidad los rechazó
 * @param precioTotal
 *            el que capturó el vendedor al pasar a Pago o el que corrigió
 *            Contabilidad; los pagos tienen que cubrirlo. Nulo en prospectos
 *            que ya estaban en Pago antes de que se pidiera
 */
public record LiquidacionVista(Long prospectoId, String prospectoNombre, String vendedorNombre,
		EstadoLiquidacion estado, BigDecimal total, List<PagoVista> pagos, Instant enviadaEn, String motivoRechazo,
		BigDecimal precioTotal) {

	/**
	 * @param registradoPorNombre
	 *            quién de Caja lo registró; nulo si ya no existe
	 * @param anulado
	 *            Caja lo anuló: sigue en el registro pero no cuenta en el total
	 * @param anuladoPorNombre
	 *            quién lo anuló; solo si está anulado
	 */
	public record PagoVista(Long id, BigDecimal monto, LocalDate fechaPago, String referencia,
			String registradoPorNombre, Instant registradoEn, boolean anulado, String anuladoPorNombre) {
	}
}
