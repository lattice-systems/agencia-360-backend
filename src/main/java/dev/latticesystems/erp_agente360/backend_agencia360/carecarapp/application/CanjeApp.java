package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Canje;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCanje;
import java.time.Instant;

/**
 * Un canje con su código para mostrar como QR en la agencia (V2-M12.03).
 *
 * @param recompensa
 *            el nombre de la recompensa canjeada
 */
public record CanjeApp(Long id, String codigo, String recompensa, int puntos, EstadoCanje estado, Instant venceEn,
		Instant usadoEn, Instant creadoEn) {

	static CanjeApp de(Canje canje, String recompensa) {
		return new CanjeApp(canje.getId(), canje.getCodigo(), recompensa, canje.getPuntos(), canje.getEstado(),
				canje.getVenceEn(), canje.getUsadoEn(), canje.getCreadoEn());
	}
}
