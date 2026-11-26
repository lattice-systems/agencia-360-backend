package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

/**
 * ventascrm ya creó el prospecto de una valuación (V2-M26.04); carecarapp lo
 * escucha para ligarlo a la valuación.
 */
public record ProspectoDeValuacionCreado(Long valuacionId, Long prospectoId) {
}
