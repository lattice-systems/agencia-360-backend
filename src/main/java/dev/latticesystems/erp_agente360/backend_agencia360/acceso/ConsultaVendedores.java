package dev.latticesystems.erp_agente360.backend_agencia360.acceso;

/**
 * Consulta que el módulo de acceso ofrece a otros módulos para saber si un
 * usuario puede tener prospectos a su cargo, sin exponer sus entidades.
 */
public interface ConsultaVendedores {

	/**
	 * @return {@code true} si el usuario existe, está activo y tiene el rol
	 *         VENTAS
	 */
	boolean esVendedorActivo(Long usuarioId);
}
