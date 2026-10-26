package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.DomicilioFiscal;

/**
 * Domicilio fiscal del cliente como lo captura y lo ve el CRM (ERP-1.3), en el
 * orden que pidió el PO.
 *
 * @param calleNumero
 *            calle y número, por ejemplo "Blvd. López Mateos 1102"
 * @param codigoPostal
 *            5 dígitos
 */
public record DomicilioFiscalDatos(String estado, String ciudad, String colonia, String calleNumero,
		String codigoPostal) {

	/**
	 * @throws IllegalArgumentException
	 *             si algún dato no es válido (400)
	 */
	DomicilioFiscal aDominio() {
		return DomicilioFiscal.de(estado, ciudad, colonia, calleNumero, codigoPostal);
	}

	static DomicilioFiscalDatos de(DomicilioFiscal domicilio) {
		return new DomicilioFiscalDatos(domicilio.getEstado(), domicilio.getCiudad(), domicilio.getColonia(),
				domicilio.getCalleNumero(), domicilio.getCodigoPostal());
	}
}
