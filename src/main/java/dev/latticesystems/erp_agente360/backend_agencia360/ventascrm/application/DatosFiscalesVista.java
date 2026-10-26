package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.DatosFiscales;

/**
 * Datos fiscales del cliente (ERP-1.3), con la descripción del régimen para
 * mostrarla sin repetir el catálogo.
 *
 * @param regimenFiscal
 *            clave del catálogo del SAT
 */
public record DatosFiscalesVista(String rfc, String nombre, String domicilio, String regimenFiscal,
		String regimenFiscalDescripcion) {

	/** @return {@code null} si el prospecto todavía no tiene datos fiscales */
	static DatosFiscalesVista de(DatosFiscales datos) {
		if (datos == null) {
			return null;
		}
		return new DatosFiscalesVista(datos.getRfc(), datos.getNombre(), datos.getDomicilio(),
				datos.getRegimen().getClave(), datos.getRegimen().getDescripcion());
	}
}
