package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Kilometraje que el cliente captura (V2-M05.03). */
public record NuevaLecturaKmRequest(@NotNull @Min(1) @Max(2_000_000) Integer km) {
}
