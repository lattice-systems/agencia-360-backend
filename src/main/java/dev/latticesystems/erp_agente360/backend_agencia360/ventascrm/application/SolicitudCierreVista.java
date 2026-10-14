package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.EstadoSolicitud;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import java.time.Instant;

/**
 * Solicitud de cierre como la revisa el Administrador.
 *
 * @param etapa
 *            etapa actual del prospecto
 * @param solicitanteNombre
 *            vendedor que la envió; nulo si ya no existe
 */
public record SolicitudCierreVista(Long id, Long prospectoId, String prospectoNombre, Etapa etapa, String motivo,
		Long solicitanteId, String solicitanteNombre, Instant solicitadaEn, EstadoSolicitud estado) {
}
