package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

/**
 * Estatus de una unidad del inventario (decisión del PO del 9 de octubre). Una
 * unidad recién recibida queda {@link #DISPONIBLE}. Ya no existe Apartada: la
 * unidad pasa a {@link #VENDIDO} cuando Facturación la elige (decisión del PO
 * del 19 de octubre).
 */
public enum EstatusVehiculo {
	DISPONIBLE, VENDIDO, ENTREGADO, NO_DISPONIBLE;

	/**
	 * Convierte el valor recibido en una petición.
	 *
	 * @throws IllegalArgumentException
	 *             si el valor no corresponde a ningún estatus
	 */
	public static EstatusVehiculo desde(String valor) {
		for (EstatusVehiculo estatus : values()) {
			if (estatus.name().equals(valor)) {
				return estatus;
			}
		}
		throw new IllegalArgumentException("Estatus no válido: " + valor);
	}
}
