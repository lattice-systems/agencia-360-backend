/**
 * Taller y Servicio.
 *
 * <p>
 * Expediente del vehículo del cliente (ERP HU 3.0): Servicio y el Administrador
 * buscan un vehículo por su VIN y ven sus datos, su dueño y los servicios que
 * se le han hecho, cada uno con sus renglones como el ticket de una tienda. Los
 * datos del vehículo y del cliente los da el módulo de clientes
 * ({@code clientes.VehiculosDeClientes}); los servicios viven aquí.
 *
 * <p>
 * Siguen pendientes las órdenes de servicio y tickets imprimibles (ERP HU 3.1),
 * la captura de servicios en pantalla y el historial en la app CareCar (CareCar
 * HU 3.2).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Taller y Servicio")
package dev.latticesystems.erp_agente360.backend_agencia360.servicio;
