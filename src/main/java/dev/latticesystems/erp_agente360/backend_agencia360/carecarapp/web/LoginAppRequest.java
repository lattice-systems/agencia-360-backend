package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El cliente entra con su correo (contrato v1.1). Se ignoran mayúsculas y
 * espacios al inicio y al final; un correo sin formato responde como credencial
 * inválida.
 *
 * @param dispositivo
 *            nombre del equipo, para que el cliente reconozca sus sesiones
 *            (V2-M01.09). <strong>Opcional</strong>: un cliente con una versión
 *            vieja de la app no lo manda y tiene que poder entrar igual. Si
 *            llega más largo que el máximo del contrato responde 400, en vez de
 *            cortarlo en silencio.
 */
public record LoginAppRequest(@NotBlank @Size(max = 120) String correo, @NotBlank @Size(max = 72) String contrasena,
		@Size(max = 80) String dispositivo) {
}
