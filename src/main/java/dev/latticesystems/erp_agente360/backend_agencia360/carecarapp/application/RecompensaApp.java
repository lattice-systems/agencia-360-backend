package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Recompensa;

/**
 * Una recompensa del catálogo (V2-M12.03).
 *
 * @param existencia
 *            {@code null} si no tiene límite
 */
public record RecompensaApp(Long id, String nombre, String descripcion, int puntos, Integer existencia) {

	static RecompensaApp de(Recompensa recompensa) {
		return new RecompensaApp(recompensa.getId(), recompensa.getNombre(), recompensa.getDescripcion(),
				recompensa.getPuntos(), recompensa.getExistencia());
	}
}
