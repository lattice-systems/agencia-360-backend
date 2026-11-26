package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Toda lectura que va a cambiar un pedido bloquea su fila, para que cancelar,
 * entregar y vencer no liberen las piezas dos veces.
 */
public interface PedidoAppRepository extends JpaRepository<PedidoApp, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PedidoApp p where p.id = :id")
	Optional<PedidoApp> bloquear(@Param("id") Long id);

	/** La más reciente primero; con empate de fecha, la de id mayor. */
	Page<PedidoApp> findByClienteIdOrderByCreatedAtDescIdDesc(Long clienteId, Pageable pagina);

	boolean existsByIdAndClienteId(Long id, Long clienteId);

	Optional<PedidoApp> findByClienteIdAndLlaveIdempotencia(Long clienteId, String llaveIdempotencia);

	long countByClienteIdAndEstadoIn(Long clienteId, Collection<EstadoPedido> estados);

	List<PedidoApp> findByCitaIdAndEstadoNotOrderById(Long citaId, EstadoPedido estado);

	@Query("select p.id from PedidoApp p where p.citaId = :citaId and p.estado in :estados order by p.id")
	List<Long> idsDeCita(@Param("citaId") Long citaId, @Param("estados") Collection<EstadoPedido> estados);

	@Query("select p.id from PedidoApp p where p.estado in :estados and p.venceEn <= :ahora order by p.id")
	List<Long> idsVencidos(@Param("estados") Collection<EstadoPedido> estados, @Param("ahora") Instant ahora);

	@Query("select p.id from PedidoApp p where p.clienteId = :clienteId and p.estado in :estados "
			+ "and p.venceEn <= :ahora order by p.id")
	List<Long> idsVencidosDelCliente(@Param("clienteId") Long clienteId,
			@Param("estados") Collection<EstadoPedido> estados, @Param("ahora") Instant ahora);

	/** Para el personal: los más antiguos primero. */
	Page<PedidoApp> findByEstadoInOrderByCreatedAtAscIdAsc(Collection<EstadoPedido> estados, Pageable pagina);

	/**
	 * Hasta que termine la transacción, nadie más aparta a nombre del cliente: así
	 * dos peticiones a la vez no pasan el límite ni repiten la llave.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(14, cast(:clienteId as integer))) bloqueo", nativeQuery = true)
	int bloquearCliente(@Param("clienteId") Long clienteId);
}
