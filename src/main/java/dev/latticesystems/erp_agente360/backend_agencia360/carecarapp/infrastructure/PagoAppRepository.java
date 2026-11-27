package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import java.time.Instant;
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

	/**
	 * El pedido del pago (V2-M14.04), vacío si es de una orden o no es del cliente.
	 */
	@Query("select p.pedidoId from PagoApp p where p.id = :id and p.clienteId = :clienteId")
	Optional<Long> pedidoDelPago(@Param("id") Long id, @Param("clienteId") Long clienteId);

	Optional<PagoApp> findByClienteIdAndLlaveIdempotencia(Long clienteId, String llaveIdempotencia);

	Optional<PagoApp> findFirstByOrdenIdAndEstado(Long ordenId, EstadoPago estado);

	Optional<PagoApp> findFirstByPedidoIdAndEstado(Long pedidoId, EstadoPago estado);

	/** Si el pedido tiene un intento que nació después de {@code desde}. */
	boolean existsByPedidoIdAndEstadoAndCreadoEnAfter(Long pedidoId, EstadoPago estado, Instant desde);

	List<PagoApp> findByClienteIdAndEstado(Long clienteId, EstadoPago estado);

	Page<PagoApp> findByClienteIdOrderByCreadoEnDescIdDesc(Long clienteId, Pageable pagina);

	/**
	 * Toma un candado de la transacción en curso; se suelta solo al terminarla.
	 * Serializa los cobros de una misma orden.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
	int bloquear(@Param("clave") long clave);
}
