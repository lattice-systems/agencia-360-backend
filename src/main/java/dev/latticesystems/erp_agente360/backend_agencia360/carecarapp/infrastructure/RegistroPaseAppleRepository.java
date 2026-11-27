package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RegistroPaseApple;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroPaseAppleRepository extends JpaRepository<RegistroPaseApple, Long> {

	Optional<RegistroPaseApple> findByDispositivoAndPassTypeIdAndSerial(String dispositivo, String passTypeId,
			String serial);

	List<RegistroPaseApple> findByCitaId(Long citaId);

	/** Las citas con pase registrado en ese dispositivo. */
	@Query("select c from Cita c where c.id in (select r.citaId from RegistroPaseApple r "
			+ "where r.dispositivo = :dispositivo and r.passTypeId = :passTypeId)")
	List<Cita> citasDelDispositivo(@Param("dispositivo") String dispositivo, @Param("passTypeId") String passTypeId);

	@Modifying
	@Query("delete from RegistroPaseApple r where r.dispositivo = :dispositivo and r.passTypeId = :passTypeId "
			+ "and r.serial = :serial")
	int borrar(@Param("dispositivo") String dispositivo, @Param("passTypeId") String passTypeId,
			@Param("serial") String serial);

	@Modifying
	@Query("delete from RegistroPaseApple r where r.id = :id")
	int borrarPorId(@Param("id") Long id);
}
