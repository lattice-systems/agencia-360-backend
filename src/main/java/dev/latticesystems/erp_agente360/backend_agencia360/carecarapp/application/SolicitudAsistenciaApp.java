package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoAsistencia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.SolicitudAsistencia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoAsistencia;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Solicitud de asistencia vista por el cliente (V2-M11.01); {@code lat},
 * {@code lng} y {@code comentario} son nulos si no los dio.
 */
public record SolicitudAsistenciaApp(Long id, Long vehiculoId, TipoAsistencia tipo, BigDecimal lat, BigDecimal lng,
		String comentario, EstadoAsistencia estado, Instant creadaEn) {

	static SolicitudAsistenciaApp de(SolicitudAsistencia solicitud) {
		return new SolicitudAsistenciaApp(solicitud.getId(), solicitud.getVehiculoClienteId(), solicitud.getTipo(),
				solicitud.getLat(), solicitud.getLng(), solicitud.getComentario(), solicitud.getEstado(),
				solicitud.getCreadaEn());
	}
}
