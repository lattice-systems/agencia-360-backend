package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Canje;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCanje;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanjeRepository extends JpaRepository<Canje, Long> {

	List<Canje> findByClienteIdOrderByCreadoEnDescIdDesc(Long clienteId);

	Optional<Canje> findByCodigo(String codigo);

	boolean existsByCodigo(String codigo);

	/** Los canjes en ese estado que ya pasaron su vencimiento. */
	List<Canje> findByEstadoAndVenceEnLessThanEqualOrderByIdAsc(EstadoCanje estado, Instant ahora);
}
