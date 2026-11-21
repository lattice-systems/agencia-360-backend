package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Plataforma;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Token de Firebase Cloud Messaging del teléfono (APP-6). */
public record DispositivoRequest(@NotBlank @Size(max = 4096) String token, @NotNull Plataforma plataforma) {
}
