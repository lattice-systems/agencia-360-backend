package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.NuevoPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Cómo paga el cliente: {@code tarjeta} nueva (token de prueba y vencimiento) o
 * {@code metodoPagoId} de una guardada. Nunca el número ni el CVV.
 */
public record NuevoPagoRequest(@Valid Tarjeta tarjeta, Long metodoPagoId, Boolean guardarTarjeta,
		Long perfilFiscalId) {

	public record Tarjeta(@Size(max = 64) String token, Integer mesVence, Integer anioVence) {
	}

	NuevoPago comoPago() {
		return new NuevoPago(tarjeta == null ? null : tarjeta.token(), tarjeta == null ? null : tarjeta.mesVence(),
				tarjeta == null ? null : tarjeta.anioVence(), metodoPagoId, Boolean.TRUE.equals(guardarTarjeta),
				perfilFiscalId);
	}
}
