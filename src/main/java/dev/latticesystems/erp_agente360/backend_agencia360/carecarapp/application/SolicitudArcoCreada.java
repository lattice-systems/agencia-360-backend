package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Se abrió una solicitud de derechos ARCO y hay que prepararla (V2-M22.02). Se
 * publica dentro de la transacción que la crea; quien la atiende corre después
 * de confirmarla y fuera del hilo de la petición.
 */
public record SolicitudArcoCreada(long solicitudId, long clienteId, String tipo) {
}
