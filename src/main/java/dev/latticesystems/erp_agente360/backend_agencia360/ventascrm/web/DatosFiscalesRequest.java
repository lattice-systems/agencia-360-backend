package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.DatosFiscalesCapturados;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos fiscales del cliente para la factura (ERP-1.3). El RFC se acepta en
 * minúsculas y con espacios alrededor; el dominio lo guarda en mayúsculas y
 * revisa también la fecha del RFC y que el régimen esté en el catálogo del SAT.
 *
 * @param nombre
 *            nombre completo o razón social, como en la constancia fiscal
 * @param regimenFiscal
 *            clave del catálogo c_RegimenFiscal del SAT, por ejemplo "612"
 */
public record DatosFiscalesRequest(
		@NotBlank @Pattern(regexp = RFC, message = "El RFC no tiene un formato válido") String rfc,
		@NotBlank @Size(max = 200) String nombre, @NotBlank @Size(max = 250) String domicilio,
		@NotBlank @Pattern(regexp = "\\d{3}", message = REGIMEN_INVALIDO) String regimenFiscal) {

	static final String REGIMEN_INVALIDO = "El régimen fiscal es la clave de 3 dígitos del SAT";

	/** Persona física (4 letras) o moral (3), fecha de 6 dígitos y homoclave. */
	static final String RFC = "\\s*[A-Za-zÑñ&]{3,4}\\d{6}[A-Za-z\\d]{3}\\s*";

	DatosFiscalesCapturados capturados() {
		return new DatosFiscalesCapturados(rfc, nombre, domicilio, regimenFiscal);
	}
}
