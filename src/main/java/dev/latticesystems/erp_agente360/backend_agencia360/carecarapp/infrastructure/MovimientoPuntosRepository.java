package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MovimientoPuntos;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoPuntosRepository extends JpaRepository<MovimientoPuntos, Long> {

	/** El saldo del cliente: la suma de todos sus movimientos. */
	@Query("select coalesce(sum(m.puntos), 0L) from MovimientoPuntos m where m.clienteId = :clienteId")
	long saldo(@Param("clienteId") Long clienteId);

	/** Los puntos ganados desde una fecha, para el nivel. */
	@Query("""
			select coalesce(sum(m.puntos), 0L) from MovimientoPuntos m
			where m.clienteId = :clienteId and m.puntos > 0 and m.motivo in :motivos and m.creadoEn >= :desde
			""")
	long ganadosDesde(@Param("clienteId") Long clienteId, @Param("motivos") Collection<MotivoPuntos> motivos,
			@Param("desde") Instant desde);

	Page<MovimientoPuntos> findByClienteIdOrderByCreadoEnDescIdDesc(Long clienteId, Pageable pagina);

	boolean existsByMotivoAndReferenciaTipoAndReferenciaId(MotivoPuntos motivo, String referenciaTipo,
			Long referenciaId);

	/** El movimiento de un evento; hay a lo más uno por motivo y referencia. */
	Optional<MovimientoPuntos> findByMotivoAndReferenciaTipoAndReferenciaId(MotivoPuntos motivo, String referenciaTipo,
			Long referenciaId);

	/**
	 * Bloqueo del saldo del cliente hasta que termine la transacción, para que dos
	 * canjes a la vez no lo dejen negativo.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
	int bloquearSaldo(@Param("clave") long clave);
}
