package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

/**
 * Tokens de una sesión de la app.
 *
 * @param expiresIn
 *            segundos de vida del access token
 * @param refreshToken
 *            se usa una vez; vence a los 30 días
 */
public record SesionIniciadaApp(String accessToken, long expiresIn, String refreshToken,
		boolean debeCambiarContrasena) {
}
