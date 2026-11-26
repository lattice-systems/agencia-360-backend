package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/** Lo que ha pasado con un amigo referido (V2-M12.04). */
public enum EstadoReferido {
	/** Se registró; ventascrm todavía no crea su prospecto. */
	REGISTRADO,
	/** Ya es prospecto y un vendedor lo atiende. */
	EN_SEGUIMIENTO,
	/** Su venta se finalizó y el cliente ganó sus puntos. */
	COMPRO,
	/** Su prospecto se cerró sin venta. */
	DESCARTADO;

	public boolean abierto() {
		return this == REGISTRADO || this == EN_SEGUIMIENTO;
	}
}
