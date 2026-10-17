package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

/**
 * Datos para registrar un cliente.
 *
 * @param correo
 *            puede venir vacío si el cliente rechazó la app
 * @param prospectoId
 *            prospecto del que viene, o {@code null} si se da de alta a mano
 */
public record NuevoCliente(String nombre, String correo, String telefono, Long prospectoId) {
}
