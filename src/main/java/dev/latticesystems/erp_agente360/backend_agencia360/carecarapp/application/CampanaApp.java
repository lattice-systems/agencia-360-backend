package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaSeguridad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCampana;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.GravedadCampana;
import java.time.Instant;

/**
 * Una campaña de seguridad para un vehículo del cliente (V2-M10.02).
 *
 * @param id
 *            id de la campaña ({@code campana_seguridad})
 * @param citaId
 *            la cita agendada para atenderla; nula si aún no se agenda
 */
public record CampanaApp(Long id, String codigo, String titulo, String descripcion, String queHacer,
		GravedadCampana gravedad, EstadoCampana estado, Long citaId, Instant publicadaEn) {

	static CampanaApp de(CampanaSeguridad campana, CampanaVehiculo delVehiculo) {
		return new CampanaApp(campana.getId(), campana.getCodigo(), campana.getTitulo(), campana.getDescripcion(),
				campana.getQueHacer(), campana.getGravedad(), delVehiculo.getEstado(), delVehiculo.getCitaId(),
				campana.getPublicadaEn());
	}
}
