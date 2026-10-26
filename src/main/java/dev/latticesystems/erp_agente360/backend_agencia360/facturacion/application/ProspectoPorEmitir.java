package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.DatosFiscales;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Prospecto en Facturación que todavía no tiene factura (ERP-1.3).
 *
 * @param total
 *            precio total de la venta, con IVA incluido
 * @param saldo
 *            lo que falta por pagar
 * @param receptor
 *            datos fiscales del cliente; vacío mientras no se capturen
 * @param bloqueo
 *            por qué todavía no se puede emitir; vacío si ya se puede
 */
public record ProspectoPorEmitir(Long prospectoId, String nombre, Long vendedorId, String vendedor, BigDecimal total,
		BigDecimal saldo, Instant enFacturacionDesde, DatosFiscales receptor, String bloqueo) {
}
