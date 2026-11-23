package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstimacionTomaCuentaRepository extends JpaRepository<EstimacionTomaCuenta, Long> {

	/** De la más reciente a la más antigua; con empate de fecha, la de id mayor. */
	Page<EstimacionTomaCuenta> findByClienteIdOrderByCreatedAtDescIdDesc(Long clienteId, Pageable pagina);
}
