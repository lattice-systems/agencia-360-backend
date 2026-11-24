package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TrabajoAdicional;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrabajoAdicionalRepository extends JpaRepository<TrabajoAdicional, Long> {

	List<TrabajoAdicional> findByOrdenServicioIdOrderByIdAsc(Long ordenServicioId);

	Optional<TrabajoAdicional> findByIdAndOrdenServicioId(Long id, Long ordenServicioId);

	long countByOrdenServicioIdAndEstado(Long ordenServicioId, EstadoAdicional estado);
}
