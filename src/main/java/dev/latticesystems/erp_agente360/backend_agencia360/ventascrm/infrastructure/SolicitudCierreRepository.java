package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.EstadoSolicitud;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.SolicitudCierre;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudCierreRepository extends JpaRepository<SolicitudCierre, Long> {

	/** De la más antigua a la más reciente, con su prospecto. */
	@Query("select s from SolicitudCierre s join fetch s.prospecto where s.estado = :estado"
			+ " order by s.createdAt asc, s.id asc")
	List<SolicitudCierre> buscarPorEstado(@Param("estado") EstadoSolicitud estado);
}
