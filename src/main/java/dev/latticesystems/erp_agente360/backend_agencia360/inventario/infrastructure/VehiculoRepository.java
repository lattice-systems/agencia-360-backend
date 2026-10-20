package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las listas van de la unidad más reciente a la más antigua. */
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

	/** El VIN se guarda en mayúsculas; se debe buscar igual. */
	boolean existsByVin(String vin);

	boolean existsByVinAndIdNot(String vin, Long id);

	List<Vehiculo> findAllByOrderByCreatedAtDescIdDesc();

	List<Vehiculo> findByEstatusOrderByCreatedAtDescIdDesc(EstatusVehiculo estatus);

	Optional<Vehiculo> findByIdAndEstatus(Long id, EstatusVehiculo estatus);

	/**
	 * Bloquea la fila de la unidad hasta terminar la transacción, para que dos
	 * personas no vendan la misma unidad al mismo tiempo (ERP-1.0).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehiculo v where v.id = :id")
	Optional<Vehiculo> buscarParaActualizar(@Param("id") Long id);
}
