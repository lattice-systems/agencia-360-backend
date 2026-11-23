package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Locale;

/** Billetera donde el cliente guarda el pase de su cita (V2-M25.03). */
public enum PlataformaWallet {

	APPLE, GOOGLE;

	/**
	 * @param valor
	 *            {@code apple} o {@code google}, sin importar mayúsculas
	 * @throws IllegalArgumentException
	 *             si es otro valor (se responde 400)
	 */
	public static PlataformaWallet de(String valor) {
		if (valor != null) {
			String normalizado = valor.trim().toUpperCase(Locale.ROOT);
			for (PlataformaWallet plataforma : values()) {
				if (plataforma.name().equals(normalizado)) {
					return plataforma;
				}
			}
		}
		throw new IllegalArgumentException("plataforma debe ser apple o google");
	}
}
