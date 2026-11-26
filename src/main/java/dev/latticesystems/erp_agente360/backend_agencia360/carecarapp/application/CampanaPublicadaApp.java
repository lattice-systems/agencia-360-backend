package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Lo que responde el personal al publicar una campaña (V2-M10.04).
 *
 * @param vehiculosAfectados
 *            cuántos vehículos entraron en el cruce y se avisaron
 */
public record CampanaPublicadaApp(Long id, String codigo, int vehiculosAfectados) {
}
