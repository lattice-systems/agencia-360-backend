package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

	/** El VIN se guarda en mayúsculas; se debe buscar igual. */
	boolean existsByVin(String vin);
}
