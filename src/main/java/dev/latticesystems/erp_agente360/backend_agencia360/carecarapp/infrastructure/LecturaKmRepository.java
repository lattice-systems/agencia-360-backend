package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.LecturaKm;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LecturaKmRepository extends JpaRepository<LecturaKm, Long> {

	/** Las lecturas del vehículo, de la más antigua a la más reciente. */
	List<LecturaKm> findByVehiculoClienteIdOrderByRegistradaEnAscIdAsc(Long vehiculoClienteId);
}
