package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaSeguridad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampanaSeguridadRepository extends JpaRepository<CampanaSeguridad, Long> {

	/**
	 * Las campañas del modelo (sin distinguir mayúsculas) que cubren ese año. El
	 * rango de VIN lo revisa {@link CampanaSeguridad#aplicaA}.
	 */
	@Query("""
			select c from CampanaSeguridad c
			where lower(c.modelo) = lower(:modelo) and :anio between c.anioDesde and c.anioHasta
			order by c.publicadaEn desc, c.id desc""")
	List<CampanaSeguridad> delModelo(@Param("modelo") String modelo, @Param("anio") int anio);

	boolean existsByCodigo(String codigo);
}
