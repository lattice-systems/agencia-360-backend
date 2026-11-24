package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.EtapaOrden;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtapaOrdenRepository extends JpaRepository<EtapaOrden, Long> {

	List<EtapaOrden> findByOrdenServicioIdOrderByInicioAscIdAsc(Long ordenServicioId);
}
