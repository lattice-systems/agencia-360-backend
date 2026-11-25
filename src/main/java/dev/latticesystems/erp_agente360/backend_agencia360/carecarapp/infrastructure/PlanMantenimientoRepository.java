package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PlanMantenimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanMantenimientoRepository extends JpaRepository<PlanMantenimiento, Long> {

	/**
	 * Los servicios del modelo (sin distinguir mayúsculas) vigentes para el año,
	 * por km.
	 */
	@Query("""
			select p from PlanMantenimiento p
			where lower(p.modelo) = lower(:modelo) and :anio between p.anioDesde and p.anioHasta
			order by p.km, p.id""")
	List<PlanMantenimiento> delModelo(@Param("modelo") String modelo, @Param("anio") int anio);
}
