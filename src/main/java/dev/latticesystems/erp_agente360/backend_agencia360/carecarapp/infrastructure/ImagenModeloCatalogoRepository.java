package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ImagenModeloCatalogo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenModeloCatalogoRepository extends JpaRepository<ImagenModeloCatalogo, Long> {

	List<ImagenModeloCatalogo> findByModeloCatalogoIdOrderByOrdenAsc(Long modeloCatalogoId);
}
