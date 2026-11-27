package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se manda para pedir el cambio de correo (V2-M01.07).
 *
 * @param contrasenaActual
 *            confirma que quien lo pide es el titular. El correo es la
 *            identidad con la que se entra, asi que sin esto alguien con el
 *            celular desbloqueado podria cambiarlo al suyo y quedarse con la
 *            cuenta. Mismo requisito que para cancelar la cuenta.
 */
public record CambioDeCorreoRequest(@NotBlank @Size(max = 120) String correo,
		@NotBlank @Size(max = 72) String contrasenaActual) {
}
