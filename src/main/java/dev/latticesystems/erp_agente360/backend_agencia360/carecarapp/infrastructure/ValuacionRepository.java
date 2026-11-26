package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Valuacion;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Toda lectura que va a cambiar una valuación bloquea su fila: sin el bloqueo,
 * dos fotos subidas a la vez se pisarían dentro del JSON de {@code fotos}. El
 * bloqueo debe ser la primera lectura de la fila en la transacción, para no
 * recibir una copia vieja ya cargada.
 */
public interface ValuacionRepository extends JpaRepository<Valuacion, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Valuacion v where v.id = :id")
	Optional<Valuacion> bloquear(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Valuacion v where v.id = :id and v.clienteId = :clienteId")
	Optional<Valuacion> bloquearDeCliente(@Param("id") Long id, @Param("clienteId") Long clienteId);

	@Query("select v.id from Valuacion v where v.clienteId = :clienteId and v.estado in :estados order by v.id")
	List<Long> idsDelCliente(@Param("clienteId") Long clienteId,
			@Param("estados") Collection<EstadoValuacion> estados);

	/**
	 * Las que hay que poner al día antes de mostrarlas al personal: ofertas cuya
	 * vigencia pasó y valuaciones cuya cita de inspección se canceló.
	 */
	@Query("select v.id from Valuacion v where (v.estado = "
			+ "dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion.OFERTADA "
			+ "and v.vigenciaHasta < :hoy) or (v.estado = "
			+ "dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion.CITA_AGENDADA "
			+ "and exists (select c.id from Cita c where c.id = v.citaId and c.estado = "
			+ "dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita.CANCELADA)) "
			+ "order by v.id")
	List<Long> idsAtrasadas(@Param("hoy") LocalDate hoy);

	/** Para el personal: las más antiguas primero. */
	@Query("select v from Valuacion v where v.estado in :estados order by v.createdAt asc, v.id asc")
	Page<Valuacion> porAtender(@Param("estados") Collection<EstadoValuacion> estados, Pageable pagina);

	/** Como {@link #porAtender}, solo las ligadas a esos prospectos. */
	@Query("select v from Valuacion v where v.estado in :estados and v.prospectoId in :prospectos "
			+ "order by v.createdAt asc, v.id asc")
	Page<Valuacion> porAtenderDeProspectos(@Param("estados") Collection<EstadoValuacion> estados,
			@Param("prospectos") Collection<Long> prospectos, Pageable pagina);

	Optional<Valuacion> findFirstByClienteIdAndVehiculoClienteIdAndEstadoIn(Long clienteId, Long vehiculoClienteId,
			Collection<EstadoValuacion> estados);

	/** La más reciente primero; con empate de fecha, la de id mayor. */
	Page<Valuacion> findByClienteIdOrderByCreatedAtDescIdDesc(Long clienteId, Pageable pagina);

	/** Los prospectos ligados a las valuaciones del cliente, el último primero. */
	@Query("select v.prospectoId from Valuacion v where v.clienteId = :clienteId and v.prospectoId is not null "
			+ "order by v.createdAt desc, v.id desc")
	List<Long> prospectosDelCliente(@Param("clienteId") Long clienteId);

	/**
	 * Hasta que termine la transacción, nadie más crea una valuación de ese
	 * vehículo: así dos peticiones a la vez no chocan con el índice único. Usa la
	 * forma de dos llaves para no coincidir con el bloqueo de franjas de la agenda.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(26, cast(:vehiculoId as integer))) bloqueo",
			nativeQuery = true)
	int bloquearVehiculo(@Param("vehiculoId") Long vehiculoId);
}
