package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/** Solo los ids de los prospectos de un vendedor (V2-M26.04). */
	@Query("select p.id from Prospecto p where p.vendedorId = :vendedorId")
	List<Long> idsDelVendedor(@Param("vendedorId") Long vendedorId);

	/** Prospectos de un vendedor que aún no terminan (V2-M16.01). */
	long countByVendedorIdAndEtapaNotIn(Long vendedorId, java.util.Collection<Etapa> etapas);
}
