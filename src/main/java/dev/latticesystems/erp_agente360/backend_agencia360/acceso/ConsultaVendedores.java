package dev.latticesystems.erp_agente360.backend_agencia360.acceso;

/**
 * Consulta que el módulo de acceso ofrece a otros módulos para saber si un
 * usuario puede tener prospectos a su cargo, sin exponer sus entidades.
 */
public interface ConsultaVendedores {

	/**
	 * @return {@code true} si el usuario existe, está activo y tiene el rol VENTAS
	 */
	boolean esVendedorActivo(Long usuarioId);

	/** Ids de los usuarios activos con rol VENTAS, de menor a mayor (V2-M16.01). */
	java.util.List<Long> vendedoresActivos();

	/** Nombre para mostrar del usuario, si existe (V2-M16.04). */
	java.util.Optional<String> nombreDe(Long usuarioId);
}
