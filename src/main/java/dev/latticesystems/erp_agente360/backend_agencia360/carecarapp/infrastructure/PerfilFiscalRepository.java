package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerfilFiscalRepository extends JpaRepository<PerfilFiscal, Long> {

	/** El predeterminado primero y luego del más viejo al más nuevo. */
	List<PerfilFiscal> findByClienteIdOrderByPredeterminadoDescIdAsc(Long clienteId);

	Optional<PerfilFiscal> findByIdAndClienteId(Long id, Long clienteId);

	long countByClienteId(Long clienteId);

	/**
	 * Bloquea los perfiles del cliente hasta que termine la transacción, para que
	 * dos cambios a la vez no dejen dos predeterminados ni ninguno.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
	int bloquearDelCliente(@Param("clave") long clave);
}
