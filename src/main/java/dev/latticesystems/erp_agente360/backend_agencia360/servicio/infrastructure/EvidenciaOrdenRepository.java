package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.EvidenciaOrden;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenciaOrdenRepository extends JpaRepository<EvidenciaOrden, Long> {

	List<EvidenciaOrden> findByOrdenServicioIdOrderByIdAsc(Long ordenServicioId);
}
