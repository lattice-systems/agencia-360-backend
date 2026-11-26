package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DatosFiscales;

/**
 * Lo que la app manda para dar de alta o corregir un perfil fiscal (V2-M08.05),
 * tal cual llega: lo revisa {@link DatosFiscales#validar}.
 *
 * @param predeterminado
 *            {@code true} pide que sea el predeterminado; nulo o {@code false}
 *            no cambia cuál es
 */
public record EntradaPerfilFiscal(String rfc, String razonSocial, String regimenFiscal, String codigoPostal,
		String usoCfdi, String correo, Boolean predeterminado) {

	DatosFiscales validar() {
		return DatosFiscales.validar(rfc, razonSocial, regimenFiscal, codigoPostal, usoCfdi, correo);
	}

	boolean pideSerPredeterminado() {
		return Boolean.TRUE.equals(predeterminado);
	}
}
