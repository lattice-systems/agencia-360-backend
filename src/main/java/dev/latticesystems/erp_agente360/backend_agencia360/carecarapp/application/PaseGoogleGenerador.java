package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Arma el enlace "Guardar en Google Wallet" de la cita (V2-M25.03): un JWT
 * RS256 firmado con la llave de la cuenta de servicio que lleva la clase y el
 * objeto genérico del pase. El QR es exactamente el de
 * {@code FirmaCitas.contenidoQr}.
 */
@Component
public class PaseGoogleGenerador {

	static final String URL_GUARDAR = "https://pay.google.com/gp/v/save/";

	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final ConfiguracionWallet configuracion;
	private final ObjectMapper json = new ObjectMapper();

	@Autowired
	public PaseGoogleGenerador(ConfiguracionWallet configuracion) {
		this.configuracion = configuracion;
	}

	/** @return la URL {@code https://pay.google.com/gp/v/save/<jwt>} */
	public String generar(DatosPase datos) {
		try {
			JsonNode cuenta = json.readTree(Base64.getDecoder().decode(configuracion.googleServiceAccountJsonBase64()));
			String correo = cuenta.path("client_email").asText();
			PrivateKey llave = llavePrivada(cuenta.path("private_key").asText());
			String claseId = configuracion.googleIssuerId() + ".carecar_cita";

			Map<String, Object> clase = new LinkedHashMap<>();
			clase.put("id", claseId);
			Map<String, Object> objeto = new LinkedHashMap<>();
			objeto.put("id", configuracion.googleIssuerId() + ".carecar_cita_" + datos.citaId());
			objeto.put("classId", claseId);
			objeto.put("state", "ACTIVE");
			objeto.put("cardTitle", texto("CareCar"));
			objeto.put("header", texto("Cita de servicio"));
			objeto.put("barcode", Map.of("type", "QR_CODE", "value", datos.codigoQr()));
			objeto.put("validTimeInterval", Map.of("start", Map.of("date", datos.inicio().format(ISO)), "end",
					Map.of("date", datos.fin().format(ISO))));
			List<Map<String, String>> modulos = new ArrayList<>();
			modulos.add(Map.of("id", "inicio", "header", "Fecha y hora", "body",
					datos.inicio().toLocalDate() + " " + datos.inicio().toLocalTime()));
			if (datos.vehiculoNombre() != null) {
				modulos.add(Map.of("id", "vehiculo", "header", "Vehículo", "body", datos.vehiculoNombre()));
			}
			objeto.put("textModulesData", modulos);

			Map<String, Object> carga = new LinkedHashMap<>();
			carga.put("genericClasses", List.of(clase));
			carga.put("genericObjects", List.of(objeto));

			String jwt = Jwts.builder().issuer(correo).audience().add("google").and().issuedAt(new Date())
					.claim("typ", "savetowallet").claim("origins", List.of()).claim("payload", carga)
					.signWith(llave, Jwts.SIG.RS256).compact();
			return URL_GUARDAR + jwt;
		} catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
			throw new IllegalStateException("No se pudo armar el pase de Google Wallet", ex);
		}
	}

	private static Map<String, Object> texto(String valor) {
		return Map.of("defaultValue", Map.of("language", "es-MX", "value", valor));
	}

	private static PrivateKey llavePrivada(String pem) throws GeneralSecurityException {
		String limpio = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		byte[] der = Base64.getDecoder().decode(limpio.getBytes(StandardCharsets.US_ASCII));
		return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
	}
}
