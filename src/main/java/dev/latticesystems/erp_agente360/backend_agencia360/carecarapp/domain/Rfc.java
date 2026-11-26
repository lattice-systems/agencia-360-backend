package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas del RFC para un perfil fiscal (V2-M08.05): 12 caracteres para persona
 * moral y 13 para física, una fecha real en las posiciones de la fecha y el
 * dígito verificador del SAT (módulo 11). Los RFC genéricos
 * ({@code XAXX010101000} y {@code XEXX010101000}) no se aceptan: sirven para
 * facturar al público en general, no para un perfil propio.
 */
public final class Rfc {

	private static final Pattern FORMA = Pattern
			.compile("^([A-ZÑ&]{3,4})([0-9]{2})([0-9]{2})([0-9]{2})([A-Z0-9]{2})([0-9A])$");
	private static final String VALORES = "0123456789ABCDEFGHIJKLMN&OPQRSTUVWXYZ Ñ";
	private static final int[] DIAS_DEL_MES = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

	private Rfc() {
	}

	/** El RFC sin espacios alrededor y en mayúsculas; vacío si no hay. */
	public static String normalizar(String rfc) {
		return rfc == null ? "" : rfc.strip().toUpperCase(Locale.ROOT);
	}

	/** El tipo de persona por el largo; vacío si el largo no es 12 ni 13. */
	public static Optional<TipoPersona> tipoDe(String rfc) {
		return switch (normalizar(rfc).length()) {
			case 12 -> Optional.of(TipoPersona.MORAL);
			case 13 -> Optional.of(TipoPersona.FISICA);
			default -> Optional.empty();
		};
	}

	/**
	 * Por qué el RFC no sirve, en palabras para la persona; vacío si sirve.
	 *
	 * @param rfc
	 *            ya normalizado
	 */
	public static Optional<String> problema(String rfc) {
		if (rfc.isEmpty()) {
			return Optional.of("Escribe el RFC");
		}
		if (rfc.length() != 12 && rfc.length() != 13) {
			return Optional.of("El RFC tiene 12 caracteres (persona moral) o 13 (persona física)");
		}
		Matcher partes = FORMA.matcher(rfc);
		if (!partes.matches() || partes.group(1).length() != rfc.length() - 9) {
			return Optional.of("El RFC no tiene la forma del SAT");
		}
		if ("XAXX010101000".equals(rfc) || "XEXX010101000".equals(rfc)) {
			return Optional.of("El RFC genérico no se usa en un perfil fiscal");
		}
		if (!fechaValida(Integer.parseInt(partes.group(2)), Integer.parseInt(partes.group(3)),
				Integer.parseInt(partes.group(4)))) {
			return Optional.of("La fecha del RFC no existe");
		}
		if (digitoVerificador(rfc) != rfc.charAt(rfc.length() - 1)) {
			return Optional.of("El dígito verificador del RFC no coincide; revisa la homoclave");
		}
		return Optional.empty();
	}

	/**
	 * Dígito verificador del SAT: cada uno de los primeros 12 caracteres (con un
	 * espacio al inicio si es persona moral) por su peso del 13 al 2, módulo 11.
	 */
	static char digitoVerificador(String rfc) {
		String completo = rfc.length() == 12 ? " " + rfc : rfc;
		int suma = 0;
		for (int i = 0; i < 12; i++) {
			suma += VALORES.indexOf(completo.charAt(i)) * (13 - i);
		}
		int residuo = suma % 11;
		if (residuo == 0) {
			return '0';
		}
		int digito = 11 - residuo;
		return digito == 10 ? 'A' : (char) ('0' + digito);
	}

	private static boolean fechaValida(int anio, int mes, int dia) {
		if (mes < 1 || mes > 12 || dia < 1 || dia > DIAS_DEL_MES[mes - 1]) {
			return false;
		}
		return mes != 2 || dia != 29 || anio % 4 == 0;
	}
}
