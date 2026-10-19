package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

/**
 * Por qué no se concretó la venta (CRM-1.2). Los tres motivos los fijó el PO
 * para el Sprint 2.
 *
 * <p>
 * {@link #SIN_CATEGORIA} solo lo tienen los cierres anteriores a CRM-1.2,
 * cuando el motivo se escribía a mano; al cerrar no se puede elegir.
 */
public enum MotivoCierre {
	SE_FUE_POR_OTRA_MARCA("Se fue por otra marca"), PERDIO_EL_INTERES("Perdió el interés"), RECHAZO_DE_FINANCIAMIENTO(
			"Rechazo de financiamiento"), SIN_CATEGORIA("Sin categoría");

	private final String etiqueta;

	MotivoCierre(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	/** El nombre que ve el usuario. */
	public String etiqueta() {
		return etiqueta;
	}

	/**
	 * Valida el motivo de un cierre nuevo.
	 *
	 * @throws IllegalArgumentException
	 *             si falta o es {@link #SIN_CATEGORIA}
	 */
	static MotivoCierre validarParaCerrar(MotivoCierre motivo) {
		if (motivo == null) {
			throw new IllegalArgumentException("El motivo de cierre es obligatorio");
		}
		if (motivo == SIN_CATEGORIA) {
			throw new IllegalArgumentException("Elige uno de los motivos de cierre: Se fue por otra marca, "
					+ "Perdió el interés o Rechazo de financiamiento");
		}
		return motivo;
	}
}
