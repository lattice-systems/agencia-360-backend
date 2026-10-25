package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain.Factura;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

	Optional<Factura> findByProspectoId(Long prospectoId);

	/** De la más reciente a la más antigua. */
	List<Factura> findAllByOrderByEmitidaEnDescIdDesc();

	/** Prospectos de la lista que ya tienen factura. */
	@Query("select f.prospectoId from Factura f where f.prospectoId in :prospectos")
	List<Long> prospectosFacturados(@Param("prospectos") Collection<Long> prospectoIds);

	/**
	 * Bloquea la fila del prospecto ({@code SELECT ... FOR UPDATE}) para que dos
	 * personas no le emitan factura a la vez. Es SQL nativo para no depender de las
	 * entidades de ventascrm; es la misma dependencia que ya tiene la llave foránea
	 * de la factura.
	 *
	 * @return el id, o vacío si el prospecto no existe
	 */
	@Query(value = "select id from prospecto where id = :id for update", nativeQuery = true)
	Optional<Long> bloquearProspecto(@Param("id") Long prospectoId);

	/** Folio interno consecutivo; un número que se toma no se repite. */
	@Query(value = "select nextval('factura_folio_seq')", nativeQuery = true)
	long siguienteFolio();
}
