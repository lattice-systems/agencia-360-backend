package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.Servicio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

	/** Del más reciente al más antiguo. */
	List<Servicio> findByVehiculoClienteIdOrderByFechaDescIdDesc(Long vehiculoClienteId);

	@EntityGraph(attributePaths = "renglones")
	@Query("select s from Servicio s where s.id = :id and s.vehiculoClienteId = :vehiculoClienteId")
	Optional<Servicio> obtenerConRenglones(@Param("vehiculoClienteId") Long vehiculoClienteId, @Param("id") Long id);
}
