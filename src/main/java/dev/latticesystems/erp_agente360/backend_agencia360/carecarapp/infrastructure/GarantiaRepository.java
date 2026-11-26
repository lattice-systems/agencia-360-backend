package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Garantia;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GarantiaRepository extends JpaRepository<Garantia, Long> {

	/** Las coberturas del vehículo, la que vence primero arriba. */
	List<Garantia> findByVehiculoClienteIdOrderByVenceEnAscIdAsc(Long vehiculoClienteId);

	/**
	 * Las que siguen vigentes por fecha: el aviso de por vencer solo revisa estas.
	 */
	@Query("select g from Garantia g where g.venceEn >= :hoy order by g.vehiculoClienteId, g.id")
	List<Garantia> vigentes(@Param("hoy") LocalDate hoy);
}
