package dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos;

import java.time.Instant;

/**
 * El vendedor pasó el prospecto a Finalizado: la unidad ya se entregó
 * (CRM-1.4). Lo publica ventascrm en la misma transacción del cambio de etapa.
 *
 * <p>
 * Vive en shared para que ventascrm, inventario y clientes lo compartan sin
 * depender entre sí (contrato acordado con Ricardo y Pablito en el Sprint 2).
 * Lo escuchan inventario (ERP-1.0: registra la venta) y clientes (CRM-1.3:
 * registra al cliente y crea su cuenta), con
 * {@code @ApplicationModuleListener}.
 *
 * @param correoCliente
 *            {@code null} solo cuando el cliente no quiere la app CareCar: el
 *            CRM no deja finalizar sin correo en otro caso. Sin correo no se
 *            crea la cuenta
 * @param fechaEntrega
 *            el momento en que se marcó Finalizado
 */
public record ProspectoFinalizado(Long prospectoId, Long vendedorId, String nombre, String correoCliente,
		Instant fechaEntrega) {
}
