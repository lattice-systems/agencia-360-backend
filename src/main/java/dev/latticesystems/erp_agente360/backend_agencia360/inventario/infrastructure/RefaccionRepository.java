package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefaccionRepository extends JpaRepository<Refaccion, Long> {

	/** El código se guarda en mayúsculas; se debe buscar igual. */
	boolean existsByCodigo(String codigo);

	boolean existsByCodigoAndIdNot(String codigo, Long id);

	List<Refaccion> findAllByOrderByCodigoAsc();

	/** Refacciones cuya existencia llegó al mínimo o quedó por debajo. */
	@Query("select r from Refaccion r where r.existencia <= r.minimo order by r.codigo")
	List<Refaccion> buscarBajoMinimo();

	/**
	 * Lee la refacción y bloquea su fila ({@code select ... for update}) hasta que
	 * termine la transacción, para cambiar la existencia sin que otra transacción
	 * la cambie al mismo tiempo. Solo se usa dentro de una transacción.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Refaccion r where r.id = :id")
	Optional<Refaccion> buscarParaActualizar(@Param("id") Long id);
}
