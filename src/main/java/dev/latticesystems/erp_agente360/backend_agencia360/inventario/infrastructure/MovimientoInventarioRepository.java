package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

	/** Del registro más reciente al más antiguo. */
	List<MovimientoInventario> findByRefaccionIdAndTipoOrderByFechaRegistroDescIdDesc(Long refaccionId,
			TipoMovimiento tipo);
}
