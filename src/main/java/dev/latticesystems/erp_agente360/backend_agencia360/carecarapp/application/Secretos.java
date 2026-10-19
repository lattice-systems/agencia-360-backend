package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generación de refresh tokens y códigos, y su hash para guardarlos. */
final class Secretos {

	private static final SecureRandom ALEATORIO = new SecureRandom();
	private static final int BYTES_REFRESH_TOKEN = 32;

	private Secretos() {
	}

	static String nuevoRefreshToken() {
		byte[] bytes = new byte[BYTES_REFRESH_TOKEN];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Código de 6 dígitos, con ceros a la izquierda. */
	static String nuevoCodigo() {
		return String.format("%06d", ALEATORIO.nextInt(1_000_000));
	}

	private static final String LETRAS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
	private static final String NUMEROS = "23456789";
	private static final int LARGO_CONTRASENA_TEMPORAL = 10;

	/**
	 * Contraseña temporal de 10 caracteres con letras y números, sin los que se
	 * confunden (0, O, 1, l, I).
	 */
	static String nuevaContrasenaTemporal() {
		String todos = LETRAS + NUMEROS;
		char[] contrasena = new char[LARGO_CONTRASENA_TEMPORAL];
		contrasena[0] = LETRAS.charAt(ALEATORIO.nextInt(LETRAS.length()));
		contrasena[1] = NUMEROS.charAt(ALEATORIO.nextInt(NUMEROS.length()));
		for (int i = 2; i < contrasena.length; i++) {
			contrasena[i] = todos.charAt(ALEATORIO.nextInt(todos.length()));
		}
		for (int i = contrasena.length - 1; i > 0; i--) {
			int j = ALEATORIO.nextInt(i + 1);
			char temporal = contrasena[i];
			contrasena[i] = contrasena[j];
			contrasena[j] = temporal;
		}
		return new String(contrasena);
	}

	static String sha256(String valor) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no está disponible", ex);
		}
	}
}
