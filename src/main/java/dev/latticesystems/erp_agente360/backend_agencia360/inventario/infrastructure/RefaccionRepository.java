package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefaccionRepository extends JpaRepository<Refaccion, Long> {

	/** El código se guarda en mayúsculas; se debe buscar igual. */
	boolean existsByCodigo(String codigo);
}
