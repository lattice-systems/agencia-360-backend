package dev.latticesystems.erp_agente360.backend_agencia360.clientes;

import java.util.Optional;

/**
 * Consulta de clientes para otros módulos (por ejemplo, el acceso a la app
 * CareCar). Es la interfaz pública del módulo; la implementación vive en
 * {@code clientes.application}.
 */
public interface DirectorioClientes {

	/**
	 * Busca por correo sin distinguir mayúsculas ni espacios al inicio y al final.
	 *
	 * @return vacío si no existe o si el texto no tiene formato de correo
	 */
	Optional<ClienteRegistrado> buscarPorCorreo(String correo);

	Optional<ClienteRegistrado> buscarPorId(Long id);

	/** Datos básicos de un cliente. El correo puede faltar si rechazó la app. */
	record ClienteRegistrado(Long id, String nombre, String correo) {
	}
}
