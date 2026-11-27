package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MedioPago;

/**
 * Cómo quiere pagar el cliente (V2-M08.02, V2-M08.03 y V2-M08.04): una tarjeta
 * nueva por su token y vencimiento, una guardada por su id o una billetera
 * simulada. Nunca el número ni el CVV.
 *
 * @param guardarTarjeta
 *            si la tarjeta nueva se guarda al quedar pagado
 * @param perfilFiscalId
 *            los datos fiscales para facturar; {@code null} sin factura
 * @param billetera
 *            {@code APPLE_PAY} o {@code GOOGLE_PAY} simulados; {@code null} si
 *            se paga con tarjeta
 */
public record NuevoPago(String token, Integer mesVence, Integer anioVence, Long metodoPagoId, boolean guardarTarjeta,
		Long perfilFiscalId, MedioPago billetera) {

	/** Con tarjeta, nueva o guardada. */
	public NuevoPago(String token, Integer mesVence, Integer anioVence, Long metodoPagoId, boolean guardarTarjeta,
			Long perfilFiscalId) {
		this(token, mesVence, anioVence, metodoPagoId, guardarTarjeta, perfilFiscalId, null);
	}
}
