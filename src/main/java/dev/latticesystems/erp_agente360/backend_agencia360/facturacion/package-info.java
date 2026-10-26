/**
 * Facturación.
 *
 * <p>
 * Emisión de la factura de la venta de una unidad (ERP-1.3), con folio interno
 * y PDF, sin timbrado (decisión 6 del modelo de datos). Toma de ventascrm los
 * prospectos en Facturación ({@code ConsultaFacturacion}) y vende la unidad en
 * inventario ({@code VentaDeUnidades}) en la misma transacción. La emisión se
 * bloquea si el saldo es mayor a cero o faltan los datos fiscales del cliente.
 * Publica {@code shared.eventos.FacturaEmitida}, que ventascrm escucha para
 * pasar el prospecto a Entrega (ERP-1.2).
 *
 * <p>
 * Nadie depende de este módulo, así que no forma ciclos.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Facturación")
package dev.latticesystems.erp_agente360.backend_agencia360.facturacion;
