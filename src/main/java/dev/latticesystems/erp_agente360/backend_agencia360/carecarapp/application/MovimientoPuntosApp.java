package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MovimientoPuntos;
import java.time.Instant;

/** Un movimiento de puntos (V2-M12.05). */
public record MovimientoPuntosApp(Long id, int puntos, MotivoPuntos motivo, String referenciaTipo, Long referenciaId,
		Instant creadoEn) {

	static MovimientoPuntosApp de(MovimientoPuntos movimiento) {
		return new MovimientoPuntosApp(movimiento.getId(), movimiento.getPuntos(), movimiento.getMotivo(),
				movimiento.getReferenciaTipo(), movimiento.getReferenciaId(), movimiento.getCreadoEn());
	}
}
