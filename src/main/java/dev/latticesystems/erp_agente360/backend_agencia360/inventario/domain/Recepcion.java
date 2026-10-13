package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import java.time.Instant;

/**
 * Lo que captura quien recibe una entrega de refacciones (decisión del PO del
 * 13 de octubre). La valida {@link Refaccion#recibir}.
 *
 * @param proveedor
 *            obligatorio
 * @param notas
 *            opcionales, por si la entrega trae algo especial
 * @param fechaLlegada
 *            cuándo llegó la mercancía; {@code null} si llegó al registrarla
 */
public record Recepcion(int cantidad, String proveedor, String notas, Instant fechaLlegada) {
}
