package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

/**
 * Por qué cambió una orden de servicio. Permite que quien escucha
 * {@link OrdenServicioCambiada} decida qué avisar: la app recarga con
 * cualquiera y las notificaciones (V2-M06.07) pueden avisar solo de etapas y
 * adicionales.
 */
public enum MotivoCambio {
	REGISTRO, APERTURA, ETAPA, INSPECCION, ADICIONAL, RESPUESTA, RENGLON
}
