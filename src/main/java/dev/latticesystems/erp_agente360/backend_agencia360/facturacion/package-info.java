/**
 * Facturación y Administración.
 *
 * <p>
 * Autorización de entrega por facturación y timbrado CFDI consumiendo la API de
 * un PAC externo (proveedor pendiente de definir), sin desarrollo interno del
 * timbrado (ERP HU 1.2). La emisión de factura se bloquea si el saldo pendiente
 * es mayor a cero. Publica el evento de factura timbrada consumido por
 * Ventas/CRM.
 *
 * <p>
 * La división final de módulos está pendiente de definir (ver sección 9 del
 * documento de arquitectura); este límite es una propuesta inicial basada en
 * las áreas funcionales del backlog.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Facturación y Administración")
package dev.latticesystems.erp_agente360.backend_agencia360.facturacion;
