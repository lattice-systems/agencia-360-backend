package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.SesionApp;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionAppRepository extends JpaRepository<SesionApp, Long> {

	Optional<SesionApp> findByTokenHash(String tokenHash);

	List<SesionApp> findByCuentaIdAndRevocadaEnIsNull(Long cuentaId);
}
