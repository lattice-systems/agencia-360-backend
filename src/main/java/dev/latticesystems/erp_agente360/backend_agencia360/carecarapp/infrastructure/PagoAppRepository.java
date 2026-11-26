package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoAppRepository extends JpaRepository<PagoApp, Long> {

	Optional<PagoApp> findByIdAndClienteId(Long id, Long clienteId);

	/** La orden del pago, sin cargarlo: así se lee fresco después del candado. */
	@Query("select p.ordenId from PagoApp p where p.id = :id and p.clienteId = :clienteId")
	Optional<Long> ordenDelPago(@Param("id") Long id, @Param("clienteId") Long clienteId);

	Optional<PagoApp> findByClienteIdAndLlaveIdempotencia(Long clienteId, String llaveIdempotencia);

	Optional<PagoApp> findFirstByOrdenIdAndEstado(Long ordenId, EstadoPago estado);

	List<PagoApp> findByClienteIdAndEstado(Long clienteId, EstadoPago estado);

	Page<PagoApp> findByClienteIdOrderByCreadoEnDescIdDesc(Long clienteId, Pageable pagina);

	/**
	 * Toma un candado de la transacción en curso; se suelta solo al terminarla.
	 * Serializa los cobros de una misma orden.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
	int bloquear(@Param("clave") long clave);
}
