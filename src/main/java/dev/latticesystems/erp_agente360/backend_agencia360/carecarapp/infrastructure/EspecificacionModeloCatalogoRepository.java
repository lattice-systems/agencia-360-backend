package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EspecificacionModeloCatalogo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EspecificacionModeloCatalogoRepository extends JpaRepository<EspecificacionModeloCatalogo, Long> {

	List<EspecificacionModeloCatalogo> findByModeloCatalogoIdOrderByOrdenAsc(Long modeloCatalogoId);
}
