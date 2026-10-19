package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaAppRepository extends JpaRepository<CuentaApp, Long> {

	Optional<CuentaApp> findByClienteId(Long clienteId);

	boolean existsByClienteId(Long clienteId);
}
