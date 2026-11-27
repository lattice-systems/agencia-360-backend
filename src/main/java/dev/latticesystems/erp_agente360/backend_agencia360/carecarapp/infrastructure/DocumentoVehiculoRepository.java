package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DocumentoVehiculo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoVehiculoRepository extends JpaRepository<DocumentoVehiculo, Long> {

	/** Los de un vehículo, por vencimiento (los que no tienen fecha, al final). */
	@Query("select d from DocumentoVehiculo d where d.vehiculoClienteId = :vehiculoId "
			+ "order by d.venceEn asc nulls last, d.id asc")
	List<DocumentoVehiculo> delVehiculo(@Param("vehiculoId") Long vehiculoId);
}
