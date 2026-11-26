package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Recompensa;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecompensaRepository extends JpaRepository<Recompensa, Long> {

	List<Recompensa> findByActivaTrueOrderByPuntosAscIdAsc();

	/** La recompensa con su fila bloqueada ({@code SELECT ... FOR UPDATE}). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Recompensa r where r.id = :id")
	Optional<Recompensa> bloquear(@Param("id") Long id);
}
