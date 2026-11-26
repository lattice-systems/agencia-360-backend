package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaVehiculo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampanaVehiculoRepository extends JpaRepository<CampanaVehiculo, Long> {

	List<CampanaVehiculo> findByVehiculoClienteId(Long vehiculoClienteId);

	Optional<CampanaVehiculo> findByCampanaSeguridadIdAndVehiculoClienteId(Long campanaSeguridadId,
			Long vehiculoClienteId);
}
