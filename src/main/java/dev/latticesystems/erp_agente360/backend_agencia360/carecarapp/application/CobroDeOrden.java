package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Cómo va el cobro de una orden, para su ticket (V2-M08.02).
 *
 * @param pago
 *            el pago con que quedó pagada; {@code null} si no se ha pagado
 * @param pagable
 *            se puede pagar en la app: viene del taller, ya está lista o
 *            entregada, tiene total y nadie la ha pagado
 */
public record CobroDeOrden(PagoVista pago, boolean pagable) {
}
