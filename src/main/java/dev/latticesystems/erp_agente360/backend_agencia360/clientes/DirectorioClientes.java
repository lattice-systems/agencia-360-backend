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

	/**
	 * Cambia el correo del cliente y avisa con {@link CorreoDeClienteCambiado} para
	 * que su cuenta de la app cambie de usuario (V2-T05.03).
	 *
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si el cliente no existe (404)
	 * @throws IllegalArgumentException
	 *             si el correo falta o no tiene formato de correo (400)
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException
	 *             si el correo ya es de otro cliente (409)
	 */
	ClienteRegistrado cambiarCorreo(Long clienteId, String correo);

	/**
	 * Reemplaza los datos personales del cliente (nombre, correo y teléfono) por un
	 * nombre genérico y sin contacto (V2-M22.03). La fila se conserva porque las
	 * citas, los traslados y las órdenes de servicio la referencian.
	 *
	 * @throws dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException
	 *             si el cliente no existe (404)
	 */
	void anonimizar(Long clienteId);

	/** Datos básicos de un cliente. El correo puede faltar si rechazó la app. */
	record ClienteRegistrado(Long id, String nombre, String correo) {
	}
}
