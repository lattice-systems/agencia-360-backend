package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import java.time.Instant;
import java.util.List;

/**
 * Un cliente con sus vehículos.
 *
 * @param creadoEn
 *            fecha y hora del alta
 */
public record ClienteDetalle(Long id, String nombre, String correo, String telefono, Instant creadoEn,
		List<VehiculoClienteVista> vehiculos) {

	static ClienteDetalle de(Cliente cliente) {
		return new ClienteDetalle(cliente.getId(), cliente.getNombre(), cliente.getCorreo(), cliente.getTelefono(),
				cliente.getCreatedAt(), cliente.getVehiculos().stream().map(VehiculoClienteVista::de).toList());
	}
}
