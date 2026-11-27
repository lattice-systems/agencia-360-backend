package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Credenciales del pase de Wallet (V2-M25.03). Todas son opcionales: si faltan,
 * la plataforma correspondiente no está configurada y el endpoint responde 503;
 * el arranque nunca falla.
 */
@Component
public class ConfiguracionWallet {

	private final String applePassTypeId;
	private final String appleTeamId;
	private final String appleCertP12Base64;
	private final String appleCertPassword;
	private final String appleWwdrPemBase64;
	private final String googleIssuerId;
	private final String googleServiceAccountJsonBase64;
	private final String appleWebServiceUrl;

	/** Sin servicio web de Apple: el pase no se actualiza solo (V2-M25.03). */
	public ConfiguracionWallet(String applePassTypeId, String appleTeamId, String appleCertP12Base64,
			String appleCertPassword, String appleWwdrPemBase64, String googleIssuerId,
			String googleServiceAccountJsonBase64) {
		this(applePassTypeId, appleTeamId, appleCertP12Base64, appleCertPassword, appleWwdrPemBase64, googleIssuerId,
				googleServiceAccountJsonBase64, "");
	}

	@Autowired
	public ConfiguracionWallet(@Value("${app.wallet.apple.pass-type-id:}") String applePassTypeId,
			@Value("${app.wallet.apple.team-id:}") String appleTeamId,
			@Value("${app.wallet.apple.cert-p12-base64:}") String appleCertP12Base64,
			@Value("${app.wallet.apple.cert-password:}") String appleCertPassword,
			@Value("${app.wallet.apple.wwdr-pem-base64:}") String appleWwdrPemBase64,
			@Value("${app.wallet.google.issuer-id:}") String googleIssuerId,
			@Value("${app.wallet.google.service-account-json-base64:}") String googleServiceAccountJsonBase64,
			@Value("${app.wallet.apple.web-service-url:}") String appleWebServiceUrl) {
		this.applePassTypeId = applePassTypeId;
		this.appleTeamId = appleTeamId;
		this.appleCertP12Base64 = appleCertP12Base64;
		this.appleCertPassword = appleCertPassword;
		this.appleWwdrPemBase64 = appleWwdrPemBase64;
		this.googleIssuerId = googleIssuerId;
		this.googleServiceAccountJsonBase64 = googleServiceAccountJsonBase64;
		this.appleWebServiceUrl = appleWebServiceUrl;
	}

	/** @return si están las cinco credenciales de Apple */
	public boolean appleConfigurado() {
		return hay(applePassTypeId) && hay(appleTeamId) && hay(appleCertP12Base64) && hay(appleCertPassword)
				&& hay(appleWwdrPemBase64);
	}

	/** @return si están las dos credenciales de Google */
	public boolean googleConfigurado() {
		return hay(googleIssuerId) && hay(googleServiceAccountJsonBase64);
	}

	/**
	 * @return si el pase de Apple puede actualizarse solo: además de estar
	 *         configurado, hay una URL pública HTTPS donde Apple llama (V2-M25.06)
	 */
	public boolean appleActualizable() {
		return appleConfigurado() && hay(appleWebServiceUrl) && appleWebServiceUrl.startsWith("https://");
	}

	/** @return la URL base del servicio web sin diagonal final, o vacío */
	String appleWebServiceUrl() {
		return appleWebServiceUrl == null ? "" : appleWebServiceUrl.replaceAll("/+$", "");
	}

	String applePassTypeId() {
		return applePassTypeId;
	}

	String appleTeamId() {
		return appleTeamId;
	}

	String appleCertP12Base64() {
		return appleCertP12Base64;
	}

	String appleCertPassword() {
		return appleCertPassword;
	}

	String appleWwdrPemBase64() {
		return appleWwdrPemBase64;
	}

	String googleIssuerId() {
		return googleIssuerId;
	}

	String googleServiceAccountJsonBase64() {
		return googleServiceAccountJsonBase64;
	}

	private static boolean hay(String valor) {
		return valor != null && !valor.isBlank();
	}
}
