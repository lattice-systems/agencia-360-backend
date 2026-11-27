package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ActividadEnVivo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadEnVivoRepository extends JpaRepository<ActividadEnVivo, Long> {

	Optional<ActividadEnVivo> findByOrdenServicioIdAndPushToken(Long ordenServicioId, String pushToken);

	List<ActividadEnVivo> findByOrdenServicioIdAndActivaTrue(Long ordenServicioId);

	List<ActividadEnVivo> findByClienteIdAndOrdenServicioIdAndActivaTrue(Long clienteId, Long ordenServicioId);

	List<ActividadEnVivo> findByClienteIdAndActivaTrue(Long clienteId);
}
