package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Historial de servicios y tickets de los vehículos del cliente (APP-3.2). Un
 * vehículo o servicio ajeno responde 404, como pide el contrato.
 */
@Service
public class ServiciosAppService {

	static final int ANIO_MINIMO = 1900;
	static final int ANIO_MAXIMO = 2100;

	private final VehiculosDeClientes vehiculos;
	private final HistorialDeServicios historial;

	public ServiciosAppService(VehiculosDeClientes vehiculos, HistorialDeServicios historial) {
		this.vehiculos = vehiculos;
		this.historial = historial;
	}

	/**
	 * @param anio
	 *            solo los servicios de ese año; {@code null} para todos
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si la página o el año no son válidos (400)
	 */
	public Pagina<ServicioApp> servicios(Long clienteId, Long vehiculoId, Integer anio, Integer pagina,
			Integer tamano) {
		var pedida = Pagina.pedir(pagina, tamano);
		if (anio != null && (anio < ANIO_MINIMO || anio > ANIO_MAXIMO)) {
			throw new IllegalArgumentException("El año debe estar entre " + ANIO_MINIMO + " y " + ANIO_MAXIMO);
		}
		exigirVehiculoDelCliente(clienteId, vehiculoId);
		return Pagina.de(historial.deVehiculo(vehiculoId, anio, pedida), ServicioApp::de);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	public List<Integer> anios(Long clienteId, Long vehiculoId) {
		exigirVehiculoDelCliente(clienteId, vehiculoId);
		return historial.aniosDeVehiculo(vehiculoId);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el servicio no existe o es de un vehículo ajeno (404)
	 */
	public ServicioDetalleApp detalle(Long clienteId, Long servicioId) {
		return historial.detalle(servicioId).filter(detalle -> esDelCliente(clienteId, detalle.vehiculoId()))
				.map(ServicioDetalleApp::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el servicio " + servicioId));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el servicio no existe o es de un vehículo ajeno (404)
	 */
	public byte[] ticket(Long clienteId, Long servicioId) {
		boolean propio = historial.vehiculoDelServicio(servicioId)
				.map(vehiculoId -> esDelCliente(clienteId, vehiculoId)).orElse(false);
		if (!propio) {
			throw new RecursoNoEncontradoException("No existe el servicio " + servicioId);
		}
		return historial.ticket(servicioId);
	}

	private void exigirVehiculoDelCliente(Long clienteId, Long vehiculoId) {
		if (!esDelCliente(clienteId, vehiculoId)) {
			throw new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId);
		}
	}

	private boolean esDelCliente(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(vehiculo -> vehiculo.propietario().id().equals(clienteId))
				.isPresent();
	}
}
