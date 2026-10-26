package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import java.math.BigDecimal;

/**
 * Renglón que se registra en un servicio.
 *
 * @param precioUnitario
 *            con IVA incluido
 */
public record RenglonNuevo(TipoRenglon tipo, String concepto, int cantidad, BigDecimal precioUnitario) {
}
