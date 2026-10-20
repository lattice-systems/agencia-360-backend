package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Venta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {

	@EntityGraph(attributePaths = "vehiculo")
	Optional<Venta> findByProspectoId(Long prospectoId);

	/** De la más reciente a la más antigua, con su unidad. */
	@EntityGraph(attributePaths = "vehiculo")
	List<Venta> findAllByOrderByCreatedAtDescIdDesc();

	/**
	 * Revisa en la base que el prospecto exista, sin depender del módulo ventascrm
	 * (que podrá usar ConsultaUnidades sin formar un ciclo). Es la misma
	 * dependencia que ya tiene la llave foránea de venta.
	 */
	@Query(value = "select exists (select 1 from prospecto where id = :id)", nativeQuery = true)
	boolean existeProspecto(@Param("id") Long prospectoId);
}
