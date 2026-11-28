package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReporteAppRequest(@NotBlank @Size(min = 10, max = 2000) String descripcion,
		@NotBlank @Size(max = 40) String versionApp, @NotBlank @Size(max = 60) String sistema,
		@NotBlank @Size(max = 120) String dispositivo) {
}
