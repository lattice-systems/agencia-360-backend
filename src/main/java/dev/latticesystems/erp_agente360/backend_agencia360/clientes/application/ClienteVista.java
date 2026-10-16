package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import java.time.Instant;

/**
 * Renglón de la lista de clientes del CRM.
 *
 * @param vehiculos
 *            cuántos vehículos tiene
 * @param creadoEn
 *            fecha y hora del alta
 */
public record ClienteVista(Long id, String nombre, String correo, String telefono, int vehiculos, Instant creadoEn) {

	static ClienteVista de(Cliente cliente) {
		return new ClienteVista(cliente.getId(), cliente.getNombre(), cliente.getCorreo(), cliente.getTelefono(),
				cliente.getVehiculos().size(), cliente.getCreatedAt());
	}
}
