package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ModeloCatalogo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModeloCatalogoRepository extends JpaRepository<ModeloCatalogo, Long> {

	List<ModeloCatalogo> findByActivoTrueOrderByModeloAscAnioDescVersionAsc();

	List<ModeloCatalogo> findAllByOrderByModeloAscAnioDescVersionAsc();

	@Query("select count(m) > 0 from ModeloCatalogo m where lower(m.marca) = lower(:marca) "
			+ "and lower(m.modelo) = lower(:modelo) and m.anio = :anio "
			+ "and lower(coalesce(m.version, '')) = lower(coalesce(:version, ''))")
	boolean existe(@Param("marca") String marca, @Param("modelo") String modelo, @Param("anio") int anio,
			@Param("version") String version);
}
