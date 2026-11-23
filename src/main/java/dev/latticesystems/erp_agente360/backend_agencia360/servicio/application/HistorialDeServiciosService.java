package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.OrdenServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.OrdenServicioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación de {@link HistorialDeServicios} (APP-3.2). */
@Service
public class HistorialDeServiciosService implements HistorialDeServicios {

	private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

	private final OrdenServicioRepository ordenServicioRepository;
	private final VehiculosDeClientes vehiculos;
	private final TicketPdf ticketPdf;

	public HistorialDeServiciosService(OrdenServicioRepository ordenServicioRepository, VehiculosDeClientes vehiculos,
			TicketPdf ticketPdf) {
		this.ordenServicioRepository = ordenServicioRepository;
		this.vehiculos = vehiculos;
		this.ticketPdf = ticketPdf;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ServicioDelVehiculo> deVehiculo(Long vehiculoId, Pageable pagina) {
		Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), RECIENTES_PRIMERO);
		return ordenServicioRepository.findByVehiculoClienteId(vehiculoId, ordenada)
				.map(servicio -> new ServicioDelVehiculo(servicio.getId(), servicio.getFecha(),
						servicio.getKilometraje(), servicio.getEstatus(), servicio.getTotal()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Long> vehiculoDelServicio(Long servicioId) {
		return ordenServicioRepository.findById(servicioId).map(OrdenServicio::getVehiculoClienteId);
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] ticket(Long servicioId) {
		OrdenServicio servicio = ordenServicioRepository.findById(servicioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el servicio " + servicioId));
		return ticketPdf.armar(ServicioDetalle.de(servicio, null),
				vehiculos.buscarPorId(servicio.getVehiculoClienteId()).orElse(null));
	}
}
