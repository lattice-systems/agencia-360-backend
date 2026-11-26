package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CamposInvalidosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Los datos de un perfil fiscal ya revisados con las reglas de CFDI 4.0
 * (V2-M08.05). Se construye con {@link #validar}, que junta todos los campos
 * que fallan para que la app los marque a la vez.
 *
 * @param tipo
 *            lo dice el largo del RFC
 */
public record DatosFiscales(String rfc, String razonSocial, String regimenFiscal, String codigoPostal, String usoCfdi,
		String correo, TipoPersona tipo) {

	public static final int MAX_RAZON_SOCIAL = 254;
	public static final int MAX_CORREO = 254;

	private static final Pattern CODIGO_POSTAL = Pattern.compile("[0-9]{5}");

	/**
	 * Revisa y limpia los datos: RFC en mayúsculas, razón social en mayúsculas y
	 * sin espacios de más (así la pide el SAT), régimen y uso del catálogo y
	 * válidos para el tipo de persona, el uso aceptado por el régimen y correo
	 * opcional.
	 *
	 * @throws CamposInvalidosException
	 *             con {@code DATOS_FISCALES_INVALIDOS} y lo que falla en cada campo
	 *             (400)
	 */
	public static DatosFiscales validar(String rfc, String razonSocial, String regimenFiscal, String codigoPostal,
			String usoCfdi, String correo) {
		Map<String, String> errores = new LinkedHashMap<>();
		String rfcLimpio = Rfc.normalizar(rfc);
		Rfc.problema(rfcLimpio).ifPresent(problema -> errores.put("rfc", problema));
		TipoPersona tipo = errores.containsKey("rfc") ? null : Rfc.tipoDe(rfcLimpio).orElseThrow();

		String razon = razonSocial == null ? "" : razonSocial.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
		if (razon.isEmpty()) {
			errores.put("razonSocial", "Escribe el nombre o la razón social como aparece en tu constancia");
		} else if (razon.length() > MAX_RAZON_SOCIAL) {
			errores.put("razonSocial", "El nombre pasa de " + MAX_RAZON_SOCIAL + " caracteres");
		}

		String cp = codigoPostal == null ? "" : codigoPostal.strip();
		if (!CODIGO_POSTAL.matcher(cp).matches()) {
			errores.put("codigoPostal", "El código postal tiene 5 dígitos");
		}

		String regimen = limpio(regimenFiscal);
		Optional<CatalogoFiscal.RegimenFiscal> delRegimen = CatalogoFiscal.regimen(regimen);
		if (delRegimen.isEmpty()) {
			errores.put("regimenFiscal", "Elige un régimen fiscal del catálogo del SAT");
		} else if (tipo != null && !delRegimen.get().aplicaA(tipo)) {
			errores.put("regimenFiscal", "Ese régimen no aplica a una persona " + nombre(tipo));
		}

		String uso = limpio(usoCfdi);
		Optional<CatalogoFiscal.UsoCfdi> delUso = CatalogoFiscal.uso(uso);
		if (delUso.isEmpty()) {
			errores.put("usoCfdi", "Elige un uso de CFDI del catálogo del SAT");
		} else if (tipo != null && !delUso.get().aplicaA(tipo)) {
			errores.put("usoCfdi", "Ese uso de CFDI no aplica a una persona " + nombre(tipo));
		} else if (delRegimen.isPresent() && !delUso.get().aceptaRegimen(regimen)) {
			errores.put("usoCfdi", "Ese uso de CFDI no se puede con el régimen " + regimen);
		}

		String mail = correo == null || correo.isBlank() ? null : correo.strip().toLowerCase(Locale.ROOT);
		if (mail != null && (mail.length() > MAX_CORREO || !correoValido(mail))) {
			errores.put("correo", "Revisa el correo");
		}

		if (!errores.isEmpty()) {
			throw new CamposInvalidosException(CodigosError.DATOS_FISCALES_INVALIDOS, errores);
		}
		return new DatosFiscales(rfcLimpio, razon, regimen, cp, uso, mail, tipo);
	}

	/**
	 * Un correo con una sola arroba, sin espacios, con algo antes de ella y un
	 * dominio con punto que no empieza, termina ni repite puntos. Se revisa sin
	 * expresión regular para que una entrada armada no la haga lenta.
	 */
	static boolean correoValido(String correo) {
		int arroba = correo.indexOf('@');
		if (arroba <= 0 || arroba != correo.lastIndexOf('@') || correo.chars().anyMatch(Character::isWhitespace)) {
			return false;
		}
		String dominio = correo.substring(arroba + 1);
		return dominio.contains(".") && !dominio.startsWith(".") && !dominio.endsWith(".") && !dominio.contains("..");
	}

	private static String limpio(String clave) {
		return clave == null ? "" : clave.strip().toUpperCase(Locale.ROOT);
	}

	private static String nombre(TipoPersona tipo) {
		return tipo == TipoPersona.FISICA ? "física" : "moral";
	}
}
