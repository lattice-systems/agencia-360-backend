/**
 * Taller y Servicio.
 *
 * <p>
 * Expediente del vehículo del cliente (ERP HU 3.0): Servicio y el Administrador
 * buscan un vehículo por su VIN y ven sus datos, su dueño y los servicios que
 * se le han hecho, cada uno con sus renglones como el ticket de una tienda. Los
 * datos del vehículo y del cliente los da el módulo de clientes
 * ({@code clientes.VehiculosDeClientes}); los servicios viven aquí como órdenes
 * de servicio ({@code orden_servicio}) con su mano de obra
 * ({@code orden_mano_de_obra}) y sus refacciones ({@code orden_refaccion}),
 * V2-T05.06.
 *
 * <p>
 * Taller en vivo (V2-M04): la orden se abre al entrar el vehículo y pasa por
 * sus etapas ({@code etapa_orden}); el técnico registra la inspección
 * multipunto ({@code punto_inspeccion}) con fotos y videos
 * ({@code evidencia_orden}, en el almacén de {@code archivos}) y propone
 * trabajos adicionales ({@code trabajo_adicional}) que el cliente aprueba o
 * rechaza con su firma desde la app ({@code servicio.TallerEnVivo}). Los
 * aprobados pasan a ser renglones de la orden al quedar lista, y cada cambio
 * publica {@code servicio.OrdenServicioCambiada} con su motivo para los avisos
 * en vivo y el push.
 *
 * <p>
 * Siguen pendientes los tickets imprimibles del ERP (ERP HU 3.1) y la captura
 * de servicios y etapas en pantalla, mientras el ERP siga congelado.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Taller y Servicio")
package dev.latticesystems.erp_agente360.backend_agencia360.servicio;
