package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TokenRecuperacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

	Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

	Optional<TokenRecuperacion> findFirstByCuentaAppIdAndUsadoFalseOrderByCreatedAtDescIdDesc(Long cuentaAppId);

	List<TokenRecuperacion> findByCuentaAppIdAndUsadoFalse(Long cuentaAppId);
}
