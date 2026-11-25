package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Plan de mantenimiento del vehículo con su kilometraje actual (V2-M05.01). Sin
 * plan para el modelo, {@code servicios} viene vacío y {@code proximo} nulo.
 * {@code kmFuente} es {@code taller} o {@code cliente}.
 */
public record PlanMantenimientoApp(Long vehiculoId, Integer kmActual, LocalDate kmActualizadoEn, String kmFuente,
		BigDecimal promedioKmDiario, List<ServicioMantenimientoApp> servicios, ProximoServicioApp proximo) {
}
