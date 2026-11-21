package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Conjunto cerrado de estatus de una orden de servicio en la app (pregunta
 * PO-5). Hoy el expediente solo registra servicios ya hechos, así que todos
 * salen {@link #TERMINADO}; los demás quedan reservados para cuando ERP-3.1
 * lleve el avance de la orden.
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
