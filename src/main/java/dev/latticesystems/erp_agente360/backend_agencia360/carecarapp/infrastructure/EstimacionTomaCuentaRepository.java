package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstimacionTomaCuentaRepository extends JpaRepository<EstimacionTomaCuenta, Long> {

	/** De la más reciente a la más antigua; con empate de fecha, la de id mayor. */
	Page<EstimacionTomaCuenta> findByClienteIdOrderByCreatedAtDescIdDesc(Long clienteId, Pageable pagina);

	/**
	 * Las que el cliente guardó de un vehículo desde un momento, de la más antigua
	 * a la más reciente (V2-M26.05).
	 */
	@Query("select e from EstimacionTomaCuenta e where e.clienteId = :clienteId "
			+ "and e.vehiculoClienteId = :vehiculoId and e.createdAt >= :desde order by e.createdAt asc, e.id asc")
	List<EstimacionTomaCuenta> delVehiculoDesde(@Param("clienteId") Long clienteId,
			@Param("vehiculoId") Long vehiculoId, @Param("desde") Instant desde);
}
