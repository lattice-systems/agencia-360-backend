package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoReferido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Referido;
import java.time.Instant;

/**
 * Un amigo referido (V2-M12.04). No regresa su teléfono ni su correo: el
 * cliente ya los conoce y no hace falta que viajen otra vez.
 *
 * @param puntos
 *            los que ganó el cliente si compró; 0 si no
 */
public record ReferidoApp(Long id, String nombre, EstadoReferido estado, int puntos, Instant creadoEn,
		Instant recompensadoEn) {

	static ReferidoApp de(Referido referido, int puntosPorReferido) {
		return new ReferidoApp(referido.getId(), referido.getNombre(), referido.getEstado(),
				referido.getEstado() == EstadoReferido.COMPRO ? puntosPorReferido : 0, referido.getCreadoEn(),
				referido.getRecompensadoEn());
	}
}
