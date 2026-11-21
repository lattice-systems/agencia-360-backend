package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaRepository extends JpaRepository<Cita, Long> {

	/** Citas confirmadas por fecha y franja en el rango. */
	@Query("select new dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure."
			+ "FranjaOcupada(c.fecha, c.franjaId, count(c)) " + "from Cita c where c.estado = :confirmada "
			+ "and c.fecha between :desde and :hasta group by c.fecha, c.franjaId")
	List<FranjaOcupada> ocupadas(@Param("confirmada") EstadoCita confirmada, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	long countByFechaAndFranjaIdAndEstado(LocalDate fecha, Long franjaId, EstadoCita estado);

	boolean existsByVehiculoClienteIdAndFechaAndFranjaIdAndEstado(Long vehiculoClienteId, LocalDate fecha,
			Long franjaId, EstadoCita estado);

	@Query("select c from Cita c where c.clienteId = :clienteId and c.estado in :estados "
			+ "and c.fecha between :desde and :hasta order by c.fecha asc, c.horaInicio asc, c.id asc")
	Page<Cita> delCliente(@Param("clienteId") Long clienteId, @Param("estados") Collection<EstadoCita> estados,
			@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, Pageable pagina);

	Optional<Cita> findByIdAndClienteId(Long id, Long clienteId);

	/**
	 * Bloqueo de la franja hasta que termine la transacción, para que dos citas a
	 * la vez no pasen del cupo aunque la franja no tenga filas todavía.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
	int bloquearFranja(@Param("clave") long clave);
}
