package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Firma de las URL temporales de los manuales (contrato v1.1, RIC-17): la app
 * abre el PDF sin el Bearer, así que la URL lleva su vencimiento y una firma
 * HMAC-SHA256 que solo el backend puede generar. Vence a los 15 minutos.
 */
@Component
public class FirmaManuales {

	public static final Duration VIGENCIA = Duration.ofMinutes(15);
	private static final String ALGORITMO = "HmacSHA256";
	private static final int LARGO_MINIMO_SECRETO = 32;

	private final byte[] secreto;
	private final Clock reloj;

	@Autowired
	public FirmaManuales(@Value("${app.manuales.secreto}") String secreto) {
		this(secreto, Clock.systemUTC());
	}

	FirmaManuales(String secreto, Clock reloj) {
		if (secreto == null || secreto.length() < LARGO_MINIMO_SECRETO) {
			throw new IllegalStateException(
					"app.manuales.secreto debe tener al menos " + LARGO_MINIMO_SECRETO + " caracteres");
		}
		this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
		this.reloj = reloj;
	}

	/** Vencimiento y firma para descargar el manual. */
	public record UrlFirmada(long expira, String firma) {
	}

	public UrlFirmada firmar(Long manualId) {
		long expira = Instant.now(reloj).plus(VIGENCIA).getEpochSecond();
		return new UrlFirmada(expira, calcular(manualId, expira));
	}

	/** @return {@code true} si la firma es de ese manual y no ha vencido */
	public boolean esValida(Long manualId, long expira, String firma) {
		if (firma == null || Instant.now(reloj).getEpochSecond() > expira) {
			return false;
		}
		return MessageDigest.isEqual(calcular(manualId, expira).getBytes(StandardCharsets.US_ASCII),
				firma.getBytes(StandardCharsets.US_ASCII));
	}

	private String calcular(Long manualId, long expira) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(new SecretKeySpec(secreto, ALGORITMO));
			byte[] firma = mac.doFinal((manualId + ":" + expira).getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 no está disponible", ex);
		}
	}
}
