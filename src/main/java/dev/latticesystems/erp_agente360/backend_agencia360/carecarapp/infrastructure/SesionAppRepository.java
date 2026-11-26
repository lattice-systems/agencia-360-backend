package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.SesionApp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionAppRepository extends JpaRepository<SesionApp, Long> {

	Optional<SesionApp> findByTokenHash(String tokenHash);

	List<SesionApp> findByCuentaIdAndRevocadaEnIsNull(Long cuentaId);

	/**
	 * Las sesiones vigentes de la cuenta: ni revocadas ni vencidas (V2-M01.09).
	 *
	 * <p>
	 * Ordenadas por último uso, las más recientes primero, y las que nunca se han
	 * refrescado al final. El orden se decide aquí y no en la app: la app no
	 * tendría cómo decidir qué hacer con los nulos sin acabar discrepando del
	 * servidor.
	 */
	@Query("""
			select s from SesionApp s
			where s.cuentaId = :cuentaId and s.revocadaEn is null and s.expiraEn > :ahora
			order by s.ultimoUso desc nulls last, s.id desc""")
	List<SesionApp> vigentesDe(@Param("cuentaId") Long cuentaId, @Param("ahora") Instant ahora);
}
