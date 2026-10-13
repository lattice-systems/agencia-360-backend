package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

/**
 * Etapas del prospecto decididas por el PO el 9 de octubre, en el orden del
 * embudo de venta. {@link #CERRADO} es una venta no concretada.
 */
public enum Etapa {
	PRIMER_CONTACTO, VISITA, COTIZACION, PAGO, FACTURACION, ENTREGA, CERRADO;

	/**
	 * Convierte el valor recibido en una petición.
	 *
	 * @throws IllegalArgumentException
	 *             si el valor no corresponde a ninguna etapa
	 */
	public static Etapa desde(String valor) {
		for (Etapa etapa : values()) {
			if (etapa.name().equals(valor)) {
				return etapa;
			}
		}
		throw new IllegalArgumentException("Etapa no válida: " + valor);
	}
}
