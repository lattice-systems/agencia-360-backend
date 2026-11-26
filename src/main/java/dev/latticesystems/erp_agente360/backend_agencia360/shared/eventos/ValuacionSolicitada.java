package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

/**
 * El cliente pidió una valuación formal y no tiene un prospecto abierto que
 * reutilizar (V2-M26.01). Lo publica carecarapp; ventascrm lo escucha y crea el
 * prospecto.
 */
public record ValuacionSolicitada(Long valuacionId, String nombre, String correo, String telefono) {
}
