package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Servicios de un vehículo para otros módulos: el historial y el ticket de la
 * app CareCar (APP-3.2, V2-M02.05 y V2-M02.06). Es interfaz pública del módulo;
 * la implementación vive en {@code servicio.application}. No revisa de quién es
 * el vehículo: eso lo hace quien la usa.
 */
public interface HistorialDeServicios {

	/**
	 * Del más reciente al más antiguo; la página trae su propio orden.
	 *
	 * @param anio
	 *            solo los servicios de ese año; {@code null} para todos
	 */
	Page<ServicioDelVehiculo> deVehiculo(Long vehiculoId, Integer anio, Pageable pagina);

	/** Años con servicios del vehículo, del más reciente al más antiguo. */
	List<Integer> aniosDeVehiculo(Long vehiculoId);

	/** @return el vehículo al que se le hizo el servicio, si existe */
	Optional<Long> vehiculoDelServicio(Long servicioId);

	/** El servicio con su mano de obra y sus refacciones, si existe. */
	Optional<DetalleDelServicio> detalle(Long servicioId);

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
	 * @param fechaCierre
	 *            cuándo se cerró la orden; {@code null} si sigue abierta
	 * @param total
	 *            mano de obra más refacciones, con IVA incluido
	 */
	record ServicioDelVehiculo(Long id, LocalDate fecha, int kilometraje, EstatusOrden estatus, Instant fechaCierre,
			BigDecimal total) {
	}

	/** Un renglón del ticket; {@code importe} es cantidad por precio, con IVA. */
	record RenglonDelServicio(String concepto, int cantidad, BigDecimal precio, BigDecimal importe) {
	}

	/**
	 * El ticket de un servicio. Los precios traen el IVA incluido: el total es la
	 * mano de obra más las refacciones y de ahí se desglosan subtotal e IVA.
	 */
	record DetalleDelServicio(Long id, Long vehiculoId, LocalDate fecha, int kilometraje, EstatusOrden estatus,
			Instant fechaCierre, List<RenglonDelServicio> manoDeObra, List<RenglonDelServicio> refacciones,
			BigDecimal totalManoDeObra, BigDecimal totalRefacciones, BigDecimal subtotal, BigDecimal iva,
			BigDecimal total) {
	}
}
