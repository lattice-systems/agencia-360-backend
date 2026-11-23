package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Firma del QR del comprobante de la cita (V2-M02.11). El QR dice
 * {@code CARECAR-CITA:<id>.<firma>}; la firma es HMAC-SHA256 en base64url de
 * {@code cita:<id>} con una llave que solo conoce el backend, para que
 * recepción (y el pase de Wallet) detecten un id inventado o alterado.
 */
@Component
public class FirmaCitas {

	static final String PREFIJO = "CARECAR-CITA:";
	private static final String ALGORITMO = "HmacSHA256";
	private static final int LARGO_MINIMO_SECRETO = 32;

	private final byte[] secreto;

	@Autowired
	public FirmaCitas(@Value("${app.citas.qr-secreto}") String secreto) {
		if (secreto == null || secreto.length() < LARGO_MINIMO_SECRETO) {
			throw new IllegalStateException(
					"app.citas.qr-secreto debe tener al menos " + LARGO_MINIMO_SECRETO + " caracteres");
		}
		this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
	}

	/** Texto que va dentro del QR de la cita. */
	public String contenidoQr(Long citaId) {
		return PREFIJO + citaId + "." + calcular(citaId);
	}

	/**
	 * @return el id de la cita si el contenido del QR trae una firma válida; vacío
	 *         si no
	 */
	public Optional<Long> citaDe(String contenido) {
		if (contenido == null || !contenido.startsWith(PREFIJO)) {
			return Optional.empty();
		}
		String resto = contenido.substring(PREFIJO.length());
		int punto = resto.indexOf('.');
		if (punto <= 0) {
			return Optional.empty();
		}
		try {
			Long citaId = Long.valueOf(resto.substring(0, punto));
			String firma = resto.substring(punto + 1);
			boolean valida = MessageDigest.isEqual(calcular(citaId).getBytes(StandardCharsets.US_ASCII),
					firma.getBytes(StandardCharsets.US_ASCII));
			return valida ? Optional.of(citaId) : Optional.empty();
		} catch (NumberFormatException noEsId) {
			return Optional.empty();
		}
	}

	private String calcular(Long citaId) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(new SecretKeySpec(secreto, ALGORITMO));
			byte[] firma = mac.doFinal(("cita:" + citaId).getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 no está disponible", ex);
		}
	}
}
