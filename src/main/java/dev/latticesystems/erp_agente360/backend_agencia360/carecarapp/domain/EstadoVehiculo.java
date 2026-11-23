package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Estado general del auto que el cliente quiere dar a cuenta (V2-M03.04). Cada
 * valor lleva los pasos que la fórmula resta o suma al ajuste por estado:
 * {@code EXCELENTE} vale más que {@code BUENO}, que es la base.
 */
public enum EstadoVehiculo {

	EXCELENTE(-1), BUENO(0), REGULAR(1), MALO(2);

	private final int pasos;

	EstadoVehiculo(int pasos) {
		this.pasos = pasos;
	}

	/** Pasos de deterioro respecto a {@code BUENO}. Negativo mejora el valor. */
	public int pasos() {
		return pasos;
	}
}
