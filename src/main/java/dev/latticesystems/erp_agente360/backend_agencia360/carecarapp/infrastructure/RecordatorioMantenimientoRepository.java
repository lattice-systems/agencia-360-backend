package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RecordatorioMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordatorioMantenimientoRepository extends JpaRepository<RecordatorioMantenimiento, Long> {

	/** Si ese servicio del vehículo ya se avisó. */
	boolean existsByVehiculoClienteIdAndPlanMantenimientoId(Long vehiculoClienteId, Long planMantenimientoId);
}
