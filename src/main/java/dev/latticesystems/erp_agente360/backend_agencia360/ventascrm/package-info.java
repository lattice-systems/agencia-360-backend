/**
 * Ventas y CRM de prospectos.
 *
 * <p>Liquidación y autorización de entrega de unidades (ERP HU 1.1) y seguimiento de
 * prospectos por etapas con categorización de no convertidos (CareCar HU 1.1, 1.2).
 * Publica el evento de dominio que habilita la facturación cuando una venta queda
 * liquidada, y consume el evento de factura timbrada para avanzar la etapa del
 * prospecto a "Facturado/Listo para entrega".
 *
 * <p>La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en las
 * áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Ventas y CRM")
package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;
