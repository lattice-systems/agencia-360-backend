package dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, Long> {

	Optional<Rol> findByNombre(String nombre);

	List<Rol> findAllByOrderByNombreAsc();
}
