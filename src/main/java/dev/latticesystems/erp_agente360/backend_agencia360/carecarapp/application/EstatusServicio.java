package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Conjunto cerrado de estatus de una orden de servicio en la app (pregunta
 * PO-5). Son los mismos de {@code orden_servicio.estatus}; hoy el expediente
 * solo registra servicios ya hechos, así que todos salen {@link #TERMINADO}.
 */
public enum EstatusServicio {
	/** El vehículo entró al taller. */
	RECIBIDO,
	/** El taller trabaja en el vehículo. */
	EN_PROCESO,
	/** El servicio está hecho y el vehículo está listo para recogerse. */
	TERMINADO,
	/** El cliente ya recogió el vehículo. */
	ENTREGADO
}
