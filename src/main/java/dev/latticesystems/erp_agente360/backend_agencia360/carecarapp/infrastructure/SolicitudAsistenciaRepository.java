package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoAsistencia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.SolicitudAsistencia;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudAsistenciaRepository extends JpaRepository<SolicitudAsistencia, Long> {

	/** La solicitud del cliente que sigue en alguno de los estados dados. */
	Optional<SolicitudAsistencia> findFirstByClienteIdAndEstadoInOrderByIdDesc(Long clienteId,
			Collection<EstadoAsistencia> estados);

	/** La solicitud, solo si es del cliente. */
	Optional<SolicitudAsistencia> findByIdAndClienteId(Long id, Long clienteId);
}
