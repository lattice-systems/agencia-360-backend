package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

/**
 * ventascrm ya creó el prospecto de una solicitud de estreno (V2-M16.01);
 * carecarapp lo escucha para ligarlo a la solicitud.
 */
public record ProspectoDeEstrenoCreado(Long solicitudId, Long prospectoId) {
}
