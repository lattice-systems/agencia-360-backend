package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Traslado;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrasladoRepository extends JpaRepository<Traslado, Long> {

	boolean existsByDireccionClienteId(Long direccionClienteId);

	List<Traslado> findByCitaIdOrderByIdAsc(Long citaId);

	/** Los traslados de varias citas en una sola consulta (evita el N+1 de la lista). */
	List<Traslado> findByCitaIdInOrderByIdAsc(Collection<Long> citaIds);
}
