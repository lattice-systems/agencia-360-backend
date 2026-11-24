package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recolección pedida al agendar: la dirección, el total y sus dos traslados.
 */
public record RecoleccionApp(Long direccionId, BigDecimal costoTotal, List<TrasladoApp> traslados) {
}
