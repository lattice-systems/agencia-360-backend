package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pregunta al asistente sobre un vehículo del cliente (V2-M24.03). */
public record PreguntaAsistenteRequest(@NotNull Long vehiculoId, @NotBlank @Size(max = 500) String pregunta) {
}
