package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Manual;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** La marca y el tipo se comparan sin distinguir mayúsculas. */
public interface ManualRepository extends JpaRepository<Manual, Long> {

	@Query("""
			select m from Manual m
			where lower(m.marca) = lower(:marca) and lower(m.modelo) = lower(:modelo) and m.anio = :anio""")
	Optional<Manual> buscar(@Param("marca") String marca, @Param("modelo") String modelo, @Param("anio") int anio);

	/** Para los vehículos registrados antes de guardar la marca. */
	@Query("""
			select m from Manual m
			where lower(m.modelo) = lower(:modelo) and m.anio = :anio
			order by m.marca, m.id""")
	List<Manual> sinMarca(@Param("modelo") String modelo, @Param("anio") int anio);
}
