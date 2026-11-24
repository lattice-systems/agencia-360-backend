package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;

/** Si una respuesta del asistente le sirvió al cliente (V2-M24.07). */
public record CalificacionAsistenteRequest(@NotNull Boolean util) {
}
