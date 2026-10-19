package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/** Datos del cliente que inició sesión en la app ({@code GET /perfil}). */
public record PerfilApp(Long id, String nombre, String correo) {
}
