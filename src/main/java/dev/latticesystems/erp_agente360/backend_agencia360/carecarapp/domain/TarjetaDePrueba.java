package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.util.List;
import java.util.Optional;

/**
 * Las tarjetas de prueba del procesador simulado (ADR 0009). La app nunca manda
 * el número: manda el token de la tarjeta que eligió y de aquí salen la marca,
 * los últimos 4, si es crédito o débito y qué hace el banco simulado. Un token
 * que no está aquí no se cobra.
 *
 * @param escenario
 *            qué responde el banco simulado al cobrar
 */
public record TarjetaDePrueba(String token, String marca, String ultimos4, TipoTarjeta tipo, Escenario escenario) {

	/** Qué responde el banco simulado. */
	public enum Escenario {
		APROBADA, FONDOS_INSUFICIENTES, PIDE_AUTENTICACION, AUTENTICACION_FALLA
	}

	private static final List<TarjetaDePrueba> TODAS = List.of(
			new TarjetaDePrueba("tok_sim_visa", "VISA", "4242", TipoTarjeta.CREDITO, Escenario.APROBADA),
			new TarjetaDePrueba("tok_sim_mastercard", "MASTERCARD", "4444", TipoTarjeta.CREDITO, Escenario.APROBADA),
			new TarjetaDePrueba("tok_sim_visa_debito", "VISA", "5556", TipoTarjeta.DEBITO, Escenario.APROBADA),
			new TarjetaDePrueba("tok_sim_fondos_insuficientes", "VISA", "9995", TipoTarjeta.CREDITO,
					Escenario.FONDOS_INSUFICIENTES),
			new TarjetaDePrueba("tok_sim_3ds", "VISA", "3220", TipoTarjeta.CREDITO, Escenario.PIDE_AUTENTICACION),
			new TarjetaDePrueba("tok_sim_3ds_falla", "VISA", "1629", TipoTarjeta.CREDITO,
					Escenario.AUTENTICACION_FALLA));

	public static List<TarjetaDePrueba> todas() {
		return TODAS;
	}

	public static Optional<TarjetaDePrueba> deToken(String token) {
		return TODAS.stream().filter(tarjeta -> tarjeta.token().equals(token)).findFirst();
	}

	/** Si el banco simulado pide confirmar con 3-D Secure antes de cobrar. */
	public boolean pideAutenticacion() {
		return escenario == Escenario.PIDE_AUTENTICACION || escenario == Escenario.AUTENTICACION_FALLA;
	}
}
