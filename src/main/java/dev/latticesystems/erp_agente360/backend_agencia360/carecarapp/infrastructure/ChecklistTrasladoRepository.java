package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ChecklistTraslado;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTrasladoRepository extends JpaRepository<ChecklistTraslado, Long> {

	Optional<ChecklistTraslado> findByTrasladoId(Long trasladoId);
}
