package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.PuntoInspeccion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuntoInspeccionRepository extends JpaRepository<PuntoInspeccion, Long> {

	List<PuntoInspeccion> findByOrdenServicioIdOrderByIdAsc(Long ordenServicioId);

	Optional<PuntoInspeccion> findByIdAndOrdenServicioId(Long id, Long ordenServicioId);
}
