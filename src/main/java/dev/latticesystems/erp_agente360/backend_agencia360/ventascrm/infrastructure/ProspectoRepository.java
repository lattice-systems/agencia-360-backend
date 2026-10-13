package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Las listas van de la más reciente a la más antigua. */
public interface ProspectoRepository extends JpaRepository<Prospecto, Long> {

	boolean existsByTelefono(String telefono);

	/** El correo se guarda en minúsculas; se debe buscar igual. */
	boolean existsByCorreo(String correo);

	List<Prospecto> findAllByOrderByCreatedAtDescIdDesc();

	List<Prospecto> findByEtapaOrderByCreatedAtDescIdDesc(Etapa etapa);

	List<Prospecto> findByVendedorIdOrderByCreatedAtDescIdDesc(Long vendedorId);

	List<Prospecto> findByVendedorIdAndEtapaOrderByCreatedAtDescIdDesc(Long vendedorId, Etapa etapa);

	Optional<Prospecto> findByIdAndVendedorId(Long id, Long vendedorId);
}
