package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

/**
 * Un cliente de la app refirió a un amigo que aceptó que lo contacten
 * (V2-M12.04). Lo publica carecarapp; ventascrm lo escucha y crea el prospecto,
 * como con las solicitudes de estreno.
 *
 * @param telefono
 *            10 dígitos, o {@code null} si solo dejó correo
 * @param correo
 *            o {@code null} si solo dejó teléfono
 */
public record ReferidoRegistrado(Long referidoId, String nombre, String telefono, String correo) {
}
