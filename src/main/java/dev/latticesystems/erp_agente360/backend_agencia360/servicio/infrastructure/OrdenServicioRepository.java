package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.OrdenServicio;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Long> {

	List<OrdenServicio> findByVehiculoClienteIdOrderByFechaDescIdDesc(Long vehiculoClienteId);

	Optional<OrdenServicio> findByIdAndVehiculoClienteId(Long id, Long vehiculoClienteId);

	Page<OrdenServicio> findByVehiculoClienteId(Long vehiculoClienteId, Pageable pagina);

	Page<OrdenServicio> findByVehiculoClienteIdAndFechaBetween(Long vehiculoClienteId, LocalDate desde, LocalDate hasta,
			Pageable pagina);

	@Query("select distinct extract(year from o.fecha) as anio from OrdenServicio o "
			+ "where o.vehiculoClienteId = :vehiculoClienteId order by anio desc")
	List<Integer> aniosConServicios(@Param("vehiculoClienteId") Long vehiculoClienteId);
}
