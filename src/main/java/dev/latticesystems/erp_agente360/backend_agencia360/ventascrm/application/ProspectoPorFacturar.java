package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import java.time.Instant;

/**
 * Prospecto que espera su unidad y su factura (ERP-1.0).
 *
 * @param vendedor
 *            nombre del vendedor a cargo
 * @param enFacturacionDesde
 *            cuando Contabilidad validó los pagos y pasó a Facturación
 */
public record ProspectoPorFacturar(Long id, String nombre, String telefono, String correo, Long vendedorId,
		String vendedor, Instant enFacturacionDesde) {
}
