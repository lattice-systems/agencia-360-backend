package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code authenticationToken} del pase de Apple Wallet de una cita (V2-M25.06).
 * Apple lo guarda dentro del pase y lo devuelve en cada llamada al servicio web
 * como {@code ApplePass <token>}. Se deriva con HMAC-SHA256 del mismo secreto
 * de las citas, con un prefijo propio para que no sirva como firma del QR; así
 * no hay nada que guardar ni que filtrar por cita.
 */
@Component
public class TokenPaseApple {

	private static final String ALGORITMO = "HmacSHA256";
	private static final int LARGO_MINIMO_SECRETO = 32;

	private final byte[] secreto;

	@Autowired
	public TokenPaseApple(@Value("${app.citas.qr-secreto}") String secreto) {
		if (secreto == null || secreto.length() < LARGO_MINIMO_SECRETO) {
			throw new IllegalStateException(
					"app.citas.qr-secreto debe tener al menos " + LARGO_MINIMO_SECRETO + " caracteres");
		}
		this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
	}

	/** @return el token del pase de esa cita, en hexadecimal */
	public String de(Long citaId) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(new SecretKeySpec(secreto, ALGORITMO));
			return HexFormat.of().formatHex(mac.doFinal(("pase-apple:" + citaId).getBytes(StandardCharsets.US_ASCII)));
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 no está disponible", ex);
		}
	}

	/** @return si el token es el de esa cita, comparado en tiempo constante */
	public boolean valido(Long citaId, String token) {
		return token != null && MessageDigest.isEqual(de(citaId).getBytes(StandardCharsets.US_ASCII),
				token.getBytes(StandardCharsets.US_ASCII));
	}
}
