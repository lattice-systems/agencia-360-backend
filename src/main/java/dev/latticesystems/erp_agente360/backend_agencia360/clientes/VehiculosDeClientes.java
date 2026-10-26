package dev.latticesystems.erp_agente360.backend_agencia360.clientes;

import java.util.List;
import java.util.Optional;

/**
 * Vehículos de los clientes para otros módulos: el expediente de Servicio
 * (ERP-3.0) y la lista de vehículos de la app (APP-3.1). Es interfaz pública
 * del módulo; la implementación vive en {@code clientes.application}.
 */
public interface VehiculosDeClientes {

	/**
	 * Busca por VIN completo o por parte de él, sin distinguir mayúsculas.
	 *
	 * @param vin
	 *            al menos 5 caracteres; con menos regresa una lista vacía
	 * @return a lo más 20 vehículos, por VIN
	 */
	List<VehiculoDeCliente> buscarPorVin(String vin);

	Optional<VehiculoDeCliente> buscarPorId(Long vehiculoId);

	/** Los vehículos de un cliente, en el orden en que se le asignaron. */
	List<VehiculoDeCliente> deCliente(Long clienteId);

	/**
	 * Corrige las placas y el kilometraje (ERP-3.0). Un valor vacío los deja sin
	 * capturar.
	 *
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si no existe (404)
	 * @throws IllegalArgumentException
	 *             si las placas pasan de 15 caracteres o el kilometraje es negativo
	 *             (400)
	 */
	VehiculoDeCliente corregir(Long vehiculoId, String placas, Integer kilometraje);

	/**
	 * Vehículo de un cliente. Como en el inventario y la factura, {@code modelo} es
	 * el Tipo (Sentra) y {@code anio} el Modelo (2026). La marca y la versión
	 * pueden faltar en los vehículos registrados antes de que se guardaran.
	 */
	record VehiculoDeCliente(Long id, String vin, String marca, String modelo, int anio, String version, String placas,
			Integer kilometraje, Propietario propietario) {
	}

	/** El cliente dueño del vehículo. El correo y el teléfono pueden faltar. */
	record Propietario(Long id, String nombre, String correo, String telefono) {
	}
}
