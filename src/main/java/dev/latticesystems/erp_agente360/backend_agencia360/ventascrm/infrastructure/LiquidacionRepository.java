package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Liquidacion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long> {

	Optional<Liquidacion> findByProspectoId(Long prospectoId);

	List<Liquidacion> findByProspectoIdIn(Collection<Long> prospectoIds);
}
