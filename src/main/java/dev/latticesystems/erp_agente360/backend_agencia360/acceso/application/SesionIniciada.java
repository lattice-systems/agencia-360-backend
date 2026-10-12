package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Aplicacion;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;

/** Resultado de un inicio de sesión correcto. */
public record SesionIniciada(String accessToken, String usuario, String nombre, Role rol, Aplicacion aplicacion) {
}
