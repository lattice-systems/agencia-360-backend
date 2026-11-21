package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

/**
 * Servicio registró en el expediente un servicio ya hecho (ERP-3.0): el
 * vehículo está listo. Lo escucha carecarapp para avisarle al cliente.
 */
public record ServicioRegistrado(Long servicioId, Long vehiculoId) {
}
