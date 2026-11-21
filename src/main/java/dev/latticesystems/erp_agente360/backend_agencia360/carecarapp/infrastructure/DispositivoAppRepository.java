package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DispositivoApp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispositivoAppRepository extends JpaRepository<DispositivoApp, Long> {

	Optional<DispositivoApp> findByToken(String token);

	List<DispositivoApp> findByClienteId(Long clienteId);

	@Modifying
	@Query("delete from DispositivoApp d where d.clienteId = :clienteId and d.token = :token")
	int borrar(@Param("clienteId") Long clienteId, @Param("token") String token);

	@Modifying
	@Query("delete from DispositivoApp d where d.token in :tokens")
	int borrarTokens(@Param("tokens") Collection<String> tokens);
}
