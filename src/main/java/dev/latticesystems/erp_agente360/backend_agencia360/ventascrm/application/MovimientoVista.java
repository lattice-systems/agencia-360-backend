package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.OrigenCambio;
import java.time.Instant;

/**
 * Un movimiento del historial de etapas.
 *
 * @param usuarioNombre
 *            quién hizo el cambio; nulo si no se conoce
 */
public record MovimientoVista(Etapa etapa, OrigenCambio origen, Instant fechaHora, String usuarioNombre) {
}
