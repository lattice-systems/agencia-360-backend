package dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefaccionRepository extends JpaRepository<Refaccion, Long> {

	/** El código se guarda en mayúsculas; se debe buscar igual. */
	boolean existsByCodigo(String codigo);

	boolean existsByCodigoAndIdNot(String codigo, Long id);

	List<Refaccion> findAllByOrderByCodigoAsc();

	/** Refacciones cuya existencia llegó al mínimo o quedó por debajo. */
	@Query("select r from Refaccion r where r.existencia <= r.minimo order by r.codigo")
	List<Refaccion> buscarBajoMinimo();

	/**
	 * Las publicadas en la tienda, por nombre. {@code patron} es un LIKE en
	 * minúsculas con los comodines escapados con {@code !}.
	 */
	@Query("select r from Refaccion r where r.publicadaEnApp = true "
			+ "and (:categoria is null or r.categoriaApp = :categoria) "
			+ "and (lower(r.descripcion) like :patron escape '!' or lower(r.codigo) like :patron escape '!') "
			+ "and (:sinVehiculo = true or r.universalApp = true or exists (select c.id from "
			+ "CompatibilidadRefaccion c where c.refaccion = r and lower(c.modelo) = :modelo "
			+ "and :anio between c.anioDesde and c.anioHasta)) order by r.descripcion, r.id")
	Page<Refaccion> buscarEnTienda(@Param("sinVehiculo") boolean sinVehiculo, @Param("modelo") String modelo,
			@Param("anio") int anio, @Param("categoria") CategoriaTienda categoria, @Param("patron") String patron,
			Pageable pagina);

	/**
	 * Bloquea la fila sin cargar la entidad. Sirve cuando la transacción ya pudo
	 * haber leído la refacción: hay que bloquear primero y releer después.
	 */
	@Query(value = "select id from refaccion where id = :id for update", nativeQuery = true)
	Optional<Long> bloquearFila(@Param("id") Long id);

	/**
	 * Lee la refacción y bloquea su fila ({@code select ... for update}) hasta que
	 * termine la transacción, para cambiar la existencia sin que otra transacción
	 * la cambie al mismo tiempo. Solo se usa dentro de una transacción.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Refaccion r where r.id = :id")
	Optional<Refaccion> buscarParaActualizar(@Param("id") Long id);
}
