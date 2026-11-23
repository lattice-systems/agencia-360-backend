package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrasladoRepository extends JpaRepository<Traslado, Long> {

	boolean existsByDireccionClienteId(Long direccionClienteId);

	List<Traslado> findByCitaIdOrderByIdAsc(Long citaId);
}
