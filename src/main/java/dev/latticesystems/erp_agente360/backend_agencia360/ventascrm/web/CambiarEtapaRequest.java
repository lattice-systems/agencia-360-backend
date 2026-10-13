package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import jakarta.validation.constraints.NotNull;

/**
 * Etapa a la que pasa el prospecto. PENDIENTE P4: si el cambio a
 * {@code CERRADO} debe pedir motivo desde S1 (los motivos son de CRM-1.2).
 */
public record CambiarEtapaRequest(@NotNull Etapa etapa) {
}
