package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.NuevoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MedioPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Cómo paga el cliente: {@code tarjeta} nueva (token de prueba y vencimiento),
 * {@code metodoPagoId} de una guardada o {@code billetera} simulada
 * (V2-M08.03). Nunca el número ni el CVV.
 */
public record NuevoPagoRequest(@Valid Tarjeta tarjeta, Long metodoPagoId, Boolean guardarTarjeta, Long perfilFiscalId,
		Billetera billetera) {

	public record Tarjeta(@Size(max = 64) String token, Integer mesVence, Integer anioVence) {
	}

	/** Las billeteras simuladas que acepta la app. */
	public enum Billetera {
		APPLE_PAY(MedioPago.APPLE_PAY), GOOGLE_PAY(MedioPago.GOOGLE_PAY);

		private final MedioPago medio;

		Billetera(MedioPago medio) {
			this.medio = medio;
		}
	}

	NuevoPago comoPago() {
		return new NuevoPago(tarjeta == null ? null : tarjeta.token(), tarjeta == null ? null : tarjeta.mesVence(),
				tarjeta == null ? null : tarjeta.anioVence(), metodoPagoId, Boolean.TRUE.equals(guardarTarjeta),
				perfilFiscalId, billetera == null ? null : billetera.medio);
	}
}
