package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

/** Datos para dar de alta un usuario del personal. */
public record NuevoUsuario(String usuario, String contrasena, String nombre, String rol) {
}
