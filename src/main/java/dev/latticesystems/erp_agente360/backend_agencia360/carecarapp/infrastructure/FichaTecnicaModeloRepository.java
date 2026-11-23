package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FichaTecnicaModelo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** La marca y el tipo se comparan sin distinguir mayúsculas. */
public interface FichaTecnicaModeloRepository extends JpaRepository<FichaTecnicaModelo, Long> {

	@Query("""
			select f from FichaTecnicaModelo f
			where lower(f.marca) = lower(:marca) and lower(f.modelo) = lower(:modelo) and f.anio = :anio
			order by f.orden, f.id""")
	List<FichaTecnicaModelo> buscar(@Param("marca") String marca, @Param("modelo") String modelo,
			@Param("anio") int anio);

	/** Para los vehículos registrados antes de guardar la marca. */
	@Query("""
			select f from FichaTecnicaModelo f
			where lower(f.modelo) = lower(:modelo) and f.anio = :anio
			order by f.orden, f.id""")
	List<FichaTecnicaModelo> sinMarca(@Param("modelo") String modelo, @Param("anio") int anio);
}
