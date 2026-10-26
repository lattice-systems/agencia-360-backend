package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.DatosFiscalesCapturados;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.DomicilioFiscalDatos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
		@NotBlank @Size(max = 200) String nombre, @NotNull @Valid Domicilio domicilio,
		@NotBlank @Pattern(regexp = "\\d{3}", message = REGIMEN_INVALIDO) String regimenFiscal) {

	static final String REGIMEN_INVALIDO = "El régimen fiscal es la clave de 3 dígitos del SAT";

	/** Persona física (4 letras) o moral (3), fecha de 6 dígitos y homoclave. */
	static final String RFC = "\\s*[A-Za-zÑñ&]{3,4}\\d{6}[A-Za-z\\d]{3}\\s*";

	/** Domicilio fiscal separado, como lo pidió el PO el 26 de octubre. */
	record Domicilio(@NotBlank @Size(max = 40) String estado, @NotBlank @Size(max = 60) String ciudad,
			@NotBlank @Size(max = 80) String colonia, @NotBlank @Size(max = 90) String calleNumero,
			@NotBlank @Pattern(regexp = "\\s*\\d{5}\\s*", message = "El código postal son 5 dígitos") String codigoPostal) {
	}

	DatosFiscalesCapturados capturados() {
		return new DatosFiscalesCapturados(rfc, nombre, new DomicilioFiscalDatos(domicilio.estado(), domicilio.ciudad(),
				domicilio.colonia(), domicilio.calleNumero(), domicilio.codigoPostal()), regimenFiscal);
	}
}
