package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Reglas de la contraseña de la app (contrato v1.1, RIC-9): de 8 a 72
 * caracteres, con al menos una letra y un número. 72 es el límite de BCrypt.
 */
public final class ReglasContrasena {

	public static final int LARGO_MINIMO = 8;
	public static final int LARGO_MAXIMO = 72;

	private ReglasContrasena() {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si no cumple las reglas, con el mensaje para el usuario
	 */
	public static void validar(String contrasena) {
		if (contrasena == null || contrasena.length() < LARGO_MINIMO || contrasena.length() > LARGO_MAXIMO) {
			throw new IllegalArgumentException(
					"La contraseña debe tener de " + LARGO_MINIMO + " a " + LARGO_MAXIMO + " caracteres");
		}
		boolean tieneLetra = contrasena.chars().anyMatch(Character::isLetter);
		boolean tieneNumero = contrasena.chars().anyMatch(Character::isDigit);
		if (!tieneLetra || !tieneNumero) {
			throw new IllegalArgumentException("La contraseña debe tener al menos una letra y un número");
		}
	}
}
