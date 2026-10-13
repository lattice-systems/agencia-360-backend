package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Las listas van de la unidad más reciente a la más antigua. */
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

	/** El VIN se guarda en mayúsculas; se debe buscar igual. */
	boolean existsByVin(String vin);

	List<Vehiculo> findAllByOrderByCreatedAtDescIdDesc();

	List<Vehiculo> findByEstatusOrderByCreatedAtDescIdDesc(EstatusVehiculo estatus);

	Optional<Vehiculo> findByIdAndEstatus(Long id, EstatusVehiculo estatus);
}
