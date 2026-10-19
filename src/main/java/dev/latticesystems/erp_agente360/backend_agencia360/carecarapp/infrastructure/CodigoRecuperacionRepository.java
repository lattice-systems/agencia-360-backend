package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CodigoRecuperacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

	/** El último código que no se ha usado ni cerrado. */
	Optional<CodigoRecuperacion> findFirstByCuentaIdAndUsadoEnIsNullOrderByCreatedAtDescIdDesc(Long cuentaId);

	List<CodigoRecuperacion> findByCuentaIdAndUsadoEnIsNull(Long cuentaId);
}
