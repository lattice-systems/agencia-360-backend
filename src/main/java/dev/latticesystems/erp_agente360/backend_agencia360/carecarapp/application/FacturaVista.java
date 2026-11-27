package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FacturaApp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * La factura de un pago como la ve la app (V2-M08.06), con la copia del
 * receptor que se timbró.
 *
 * @param simulada
 *            siempre {@code true}: no tiene validez fiscal (ADR 0009)
 */
public record FacturaVista(Long id, Long pagoId, String folio, UUID uuid, String rfcReceptor,
		String razonSocialReceptor, String usoCfdi, BigDecimal total, boolean simulada, Instant timbradaEn) {

	public static FacturaVista de(FacturaApp factura) {
		return new FacturaVista(factura.getId(), factura.getPagoId(), factura.getFolio(), factura.getUuidFiscal(),
				factura.getReceptorRfc(), factura.getReceptorNombre(), factura.getUsoCfdi(), factura.getTotal(), true,
				factura.getTimbradaEn());
	}
}
