package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Cómo quiere pagar el cliente (V2-M08.02 y V2-M08.04): una tarjeta nueva por su
 * token y vencimiento, o una guardada por su id. Nunca el número ni el CVV.
 *
 * @param guardarTarjeta
 *            si la tarjeta nueva se guarda al quedar pagado
 * @param perfilFiscalId
 *            los datos fiscales para facturar; {@code null} sin factura
 */
public record NuevoPago(String token, Integer mesVence, Integer anioVence, Long metodoPagoId,
		boolean guardarTarjeta, Long perfilFiscalId) {
}
