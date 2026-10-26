package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Todo lo que lleva una factura al emitirse: con esto se guarda y se arma su
 * PDF.
 */
public record DatosFactura(String folio, Long prospectoId, Long ventaId, Instant emitidaEn, Parte emisor,
		Parte receptor, Unidad unidad, Montos montos) {

	public DatosFactura {
		Objects.requireNonNull(folio, "folio");
		Objects.requireNonNull(prospectoId, "prospectoId");
		Objects.requireNonNull(ventaId, "ventaId");
		Objects.requireNonNull(emitidaEn, "emitidaEn");
		Objects.requireNonNull(emisor, "emisor");
		Objects.requireNonNull(receptor, "receptor");
		Objects.requireNonNull(unidad, "unidad");
		Objects.requireNonNull(montos, "montos");
	}

	/** Datos fiscales del emisor (la agencia) o del receptor (el cliente). */
	public record Parte(String rfc, String nombre, String domicilioFiscal, String regimenFiscal) {
	}

	/**
	 * La unidad vendida. {@code modelo} es el Tipo (Sentra) y {@code anio} el
	 * Modelo (2026).
	 */
	public record Unidad(Long vehiculoId, String vin, String marca, String modelo, String version, Integer anio,
			String color) {
	}
}
