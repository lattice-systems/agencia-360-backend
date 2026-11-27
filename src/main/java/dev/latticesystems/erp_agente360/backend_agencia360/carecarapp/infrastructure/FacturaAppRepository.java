package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FacturaApp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las facturas de los pagos de la app (V2-M08.06). */
public interface FacturaAppRepository extends JpaRepository<FacturaApp, Long> {

	Optional<FacturaApp> findByIdAndClienteId(Long id, Long clienteId);

	/**
	 * La factura de un pago. Va con JPQL porque {@code FacturaApp} expone
	 * {@code getPagoId()} y una consulta derivada {@code findByPagoId} buscaría un
	 * atributo {@code pagoId} que la entidad no mapea.
	 */
	@Query("select f from FacturaApp f where f.pago.id = :pagoId")
	Optional<FacturaApp> buscarPorPago(@Param("pagoId") Long pagoId);

	/**
	 * El siguiente número de la misma secuencia que el ERP; un número que se toma
	 * no se repite y la serie {@code A} lo distingue de los {@code F-} del ERP.
	 */
	@Query(value = "select nextval('factura_folio_seq')", nativeQuery = true)
	long siguienteFolio();
}
