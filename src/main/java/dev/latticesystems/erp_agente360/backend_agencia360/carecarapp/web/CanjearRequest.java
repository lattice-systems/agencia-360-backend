package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;

/** La recompensa que el cliente quiere canjear (V2-M12.03). */
public record CanjearRequest(@NotNull Long recompensaId) {
}
