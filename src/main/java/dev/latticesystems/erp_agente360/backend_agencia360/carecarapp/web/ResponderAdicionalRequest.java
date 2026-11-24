package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Respuesta del cliente a un trabajo adicional (V2-M04.05): aprobar o rechazar,
 * siempre con la firma en pantalla como PNG en Base64.
 */
public record ResponderAdicionalRequest(@NotNull Boolean aprobado, @NotBlank @Size(max = 1_400_000) String firma) {
}
