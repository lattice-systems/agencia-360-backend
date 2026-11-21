package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Historial de servicios y tickets de los vehículos del cliente (APP-3.2). Un
 * vehículo o servicio ajeno responde 404, como pide el contrato.
 */
@Service
public class ServiciosAppService {

	private final VehiculosDeClientes vehiculos;
	private final HistorialDeServicios historial;

	public ServiciosAppService(VehiculosDeClientes vehiculos, HistorialDeServicios historial) {
		this.vehiculos = vehiculos;
		this.historial = historial;
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si la página no es válida (400)
	 */
	public Pagina<ServicioApp> servicios(Long clienteId, Long vehiculoId, Integer pagina, Integer tamano) {
		var pedida = Pagina.pedir(pagina, tamano);
		if (!esDelCliente(clienteId, vehiculoId)) {
			throw new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId);
		}
		return Pagina.de(historial.deVehiculo(vehiculoId, pedida), ServicioApp::de);
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

	private boolean esDelCliente(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(vehiculo -> vehiculo.propietario().id().equals(clienteId))
				.isPresent();
	}
}
