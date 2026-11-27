package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Credenciales de APNs (V2-M25.05 y V2-M25.06): la llave {@code .p8} de
 * autenticación por token, el equipo de Apple y la app. Todas vienen de
 * variables de entorno y son opcionales (R8): sin ellas los pushes se
 * desactivan sin fallar y el arranque sigue normal.
 *
 * <p>
 * {@code sandbox} solo aplica a las Live Activities: las compilaciones de
 * desarrollo registran tokens del servidor de pruebas de Apple. Los pases de
 * Wallet siempre usan producción.
 */
@Component
public class ConfiguracionApns {

	private final String keyId;
	private final String teamId;
	private final String keyP8Base64;
	private final String bundleId;
	private final boolean sandbox;

	@Autowired
	public ConfiguracionApns(@Value("${app.apns.key-id:}") String keyId, @Value("${app.apns.team-id:}") String teamId,
			@Value("${app.apns.key-p8-base64:}") String keyP8Base64, @Value("${app.apns.bundle-id:}") String bundleId,
			@Value("${app.apns.sandbox:false}") boolean sandbox) {
		this.keyId = keyId;
		this.teamId = teamId;
		this.keyP8Base64 = keyP8Base64;
		this.bundleId = bundleId;
		this.sandbox = sandbox;
	}

	/** @return si están la llave, el equipo y el identificador de la llave */
	public boolean configurado() {
		return hay(keyId) && hay(teamId) && hay(keyP8Base64);
	}

	/** @return si además del acceso a APNs se conoce el identificador de la app */
	public boolean actividadesConfiguradas() {
		return configurado() && hay(bundleId);
	}

	public boolean sandbox() {
		return sandbox;
	}

	String keyId() {
		return keyId;
	}

	String teamId() {
		return teamId;
	}

	String keyP8Base64() {
		return keyP8Base64;
	}

	public String bundleId() {
		return bundleId;
	}

	private static boolean hay(String valor) {
		return valor != null && !valor.isBlank();
	}
}
