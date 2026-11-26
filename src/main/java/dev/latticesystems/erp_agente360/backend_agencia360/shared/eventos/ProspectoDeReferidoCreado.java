package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

/**
 * ventascrm ya creó el prospecto de un referido (V2-M12.04); carecarapp lo
 * escucha para ligarlo y, cuando la venta se finalice, dar los puntos.
 */
public record ProspectoDeReferidoCreado(Long referidoId, Long prospectoId) {
}
