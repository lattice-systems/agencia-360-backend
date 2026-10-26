package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.DatosFiscales;

/**
 * Datos fiscales del cliente como los captura el vendedor en el CRM (ERP-1.3).
 *
 * @param regimenFiscal
 *            clave del catálogo del SAT, por ejemplo "612"
 */
public record DatosFiscalesCapturados(String rfc, String nombre, DomicilioFiscalDatos domicilio, String regimenFiscal) {

	/**
	 * @throws IllegalArgumentException
	 *             si algún dato no es válido (400)
	 */
	DatosFiscales aDominio() {
		return DatosFiscales.de(rfc, nombre, domicilio == null ? null : domicilio.aDominio(), regimenFiscal);
	}
}
