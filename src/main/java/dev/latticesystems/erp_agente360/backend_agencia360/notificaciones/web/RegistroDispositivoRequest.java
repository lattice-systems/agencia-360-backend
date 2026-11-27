package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Token de push de una instalación (V2-M06.02).
 *
 * @param idioma
 *            opcional; sin él se asume {@code es}
 */
public record RegistroDispositivoRequest(@NotBlank @Size(max = 4096) String tokenFcm,
		@NotBlank @Pattern(regexp = "(?i)android|ios", message = "debe ser android o ios") String plataforma,
		@Size(max = 10) String idioma) {
}
