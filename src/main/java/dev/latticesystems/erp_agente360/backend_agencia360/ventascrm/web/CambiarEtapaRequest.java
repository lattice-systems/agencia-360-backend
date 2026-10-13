package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Etapa a la que pasa el prospecto. El motivo es obligatorio al pasar a
 * {@code CERRADO} (decisión del PO del 12 de octubre) y se ignora en las demás
 * etapas; esa regla la valida el dominio.
 */
public record CambiarEtapaRequest(@NotNull Etapa etapa, @Size(max = 200) String motivoCierre) {
}
