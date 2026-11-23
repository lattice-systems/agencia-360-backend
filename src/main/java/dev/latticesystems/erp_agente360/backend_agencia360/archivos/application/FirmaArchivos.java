package dev.latticesystems.erp_agente360.backend_agencia360.archivos.application;

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

@Component
public class FirmaArchivos {

	public static final Duration VIGENCIA = Duration.ofMinutes(15);
	private static final String ALGORITMO = "HmacSHA256";
	private static final int LARGO_MINIMO_SECRETO = 32;

	private final byte[] secreto;
	private final Clock reloj;

	@Autowired
	public FirmaArchivos(@Value("${app.archivos.secreto}") String secreto) {
		this(secreto, Clock.systemUTC());
	}

	FirmaArchivos(String secreto, Clock reloj) {
		if (secreto == null || secreto.length() < LARGO_MINIMO_SECRETO) {
			throw new IllegalStateException(
					"app.archivos.secreto debe tener al menos " + LARGO_MINIMO_SECRETO + " caracteres");
		}
		this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
		this.reloj = reloj;
	}

	public record UrlFirmada(long expira, String firma) {
	}

	public UrlFirmada firmar(String clave) {
		long expira = Instant.now(reloj).plus(VIGENCIA).getEpochSecond();
		return new UrlFirmada(expira, calcular(clave, expira));
	}

	public boolean esValida(String clave, long expira, String firma) {
		if (clave == null || firma == null || Instant.now(reloj).getEpochSecond() > expira) {
			return false;
		}
		return MessageDigest.isEqual(calcular(clave, expira).getBytes(StandardCharsets.US_ASCII),
				firma.getBytes(StandardCharsets.US_ASCII));
	}

	private String calcular(String clave, long expira) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(new SecretKeySpec(secreto, ALGORITMO));
			byte[] firma = mac.doFinal(("archivo:" + clave + ":" + expira).getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 no está disponible", ex);
		}
	}
}
