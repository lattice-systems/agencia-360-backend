package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ResultadoInspeccion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Un punto de la inspección multipunto con su semáforo (V2-M04.04). */
public record RevisarPuntoRequest(@NotBlank @Size(max = 120) String punto, @NotNull ResultadoInspeccion resultado,
		@Size(max = 500) String comentario) {
}
