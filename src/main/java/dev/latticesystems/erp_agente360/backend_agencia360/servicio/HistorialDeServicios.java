package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Servicios de un vehículo para otros módulos: el historial y el ticket de la
 * app CareCar (APP-3.2). Es interfaz pública del módulo; la implementación vive
 * en {@code servicio.application}. No revisa de quién es el vehículo: eso lo
 * hace quien la usa.
 */
public interface HistorialDeServicios {

	/** Del más reciente al más antiguo; la página trae su propio orden. */
	Page<ServicioDelVehiculo> deVehiculo(Long vehiculoId, Pageable pagina);

	/** @return el vehículo al que se le hizo el servicio, si existe */
	Optional<Long> vehiculoDelServicio(Long servicioId);

	/**
	 * El ticket del servicio en PDF: vehículo, renglones y montos.
	 *
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si el servicio no existe (404)
	 */
	byte[] ticket(Long servicioId);

	/**
	 * Un servicio del vehículo.
	 *
	 * @param estatus
	 *            estatus de la orden de servicio
	 * @param total
	 *            mano de obra más refacciones, con IVA incluido
	 */
	record ServicioDelVehiculo(Long id, LocalDate fecha, int kilometraje, EstatusOrden estatus, BigDecimal total) {
	}
}
