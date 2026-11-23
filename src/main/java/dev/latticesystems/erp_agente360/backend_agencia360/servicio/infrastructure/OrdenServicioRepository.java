package dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.OrdenServicio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Long> {

	List<OrdenServicio> findByVehiculoClienteIdOrderByFechaDescIdDesc(Long vehiculoClienteId);

	Optional<OrdenServicio> findByIdAndVehiculoClienteId(Long id, Long vehiculoClienteId);

	Page<OrdenServicio> findByVehiculoClienteId(Long vehiculoClienteId, Pageable pagina);
}
