package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

/**
 * Etapas de la línea de tiempo de una orden de servicio (V2-M04.02), en el
 * orden en que pasan. Cada una fija el estatus de la orden: recibida, en
 * proceso, terminada al quedar lista para entrega y entregada.
 */
public enum EtapaTaller {
	RECIBIDO(EstatusOrden.RECIBIDO), DIAGNOSTICO(EstatusOrden.EN_PROCESO), ESPERANDO_APROBACION(
			EstatusOrden.EN_PROCESO), EN_REPARACION(EstatusOrden.EN_PROCESO), CONTROL_DE_CALIDAD(
					EstatusOrden.EN_PROCESO), LAVADO(EstatusOrden.EN_PROCESO), LISTO_PARA_ENTREGA(
							EstatusOrden.TERMINADO), ENTREGADO(EstatusOrden.ENTREGADO);

	private final EstatusOrden estatus;

	EtapaTaller(EstatusOrden estatus) {
		this.estatus = estatus;
	}

	public EstatusOrden estatus() {
		return estatus;
	}

	/** Avance de la orden en esta etapa, de 0 (recibida) a 100 (entregada). */
	public int avance() {
		return Math.round(ordinal() * 100f / (values().length - 1));
	}
}
