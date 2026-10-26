package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Lo que debe cumplir un prospecto en Facturación para emitirle la factura
 * (reglas 2 y 4 del Sprint 3). La pantalla lo muestra antes de intentar y la
 * emisión lo vuelve a revisar.
 */
final class ReglasDeEmision {

	private static final Locale MEXICO = Locale.forLanguageTag("es-MX");

	private ReglasDeEmision() {
	}

	/** @return por qué no se puede emitir; vacío si ya se puede */
	static Optional<String> bloqueo(ProspectoParaFacturar prospecto) {
		if (prospecto.total() == null || prospecto.total().signum() <= 0) {
			return Optional.of("El prospecto no tiene precio total de la venta");
		}
		if (prospecto.saldo() == null || prospecto.saldo().signum() > 0) {
			BigDecimal saldo = prospecto.saldo() == null ? prospecto.total() : prospecto.saldo();
			return Optional.of("Tiene un saldo de " + NumberFormat.getCurrencyInstance(MEXICO).format(saldo)
					+ ": la factura se emite con el saldo en 0");
		}
		if (prospecto.receptor() == null || !prospecto.receptor().completos()) {
			return Optional.of("Faltan los datos fiscales del cliente (RFC, nombre completo, domicilio fiscal y "
					+ "régimen fiscal); los captura el vendedor al pasar a Pagos");
		}
		return Optional.empty();
	}
}
