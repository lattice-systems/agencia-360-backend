/**
 * Taller y Servicio.
 *
 * <p>
 * Órdenes de servicio y tickets, imprimibles/exportables (ERP HU 3.1). Cada
 * ticket queda vinculado al vehículo y visible en el historial de la app
 * CareCar (CareCar HU 3.2). El MVP del ERP no incluye esta épica; falta definir
 * qué parte de Servicio entra al MVP para que el historial y la agenda de citas
 * de la app tengan datos (ver observación sobre el MVP en el documento de
 * arquitectura).
 *
 * <p>
 * La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en
 * las áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Taller y Servicio")
package dev.latticesystems.erp_agente360.backend_agencia360.servicio;
