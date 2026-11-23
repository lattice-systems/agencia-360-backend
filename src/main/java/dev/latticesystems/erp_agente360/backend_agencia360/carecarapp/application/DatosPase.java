package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.ZonedDateTime;

/**
 * Lo que lleva un pase de Wallet de una cita.
 *
 * @param citaId
 *            id de la cita
 * @param codigoQr
 *            texto exacto de {@code FirmaCitas.contenidoQr(citaId)}
 * @param inicio
 *            inicio de la cita en hora de la agencia
 * @param fin
 *            fin de la cita; el pase vence aquí
 * @param vehiculoNombre
 *            nombre del vehículo, o {@code null}
 */
public record DatosPase(Long citaId, String codigoQr, ZonedDateTime inicio, ZonedDateTime fin, String vehiculoNombre) {
}
