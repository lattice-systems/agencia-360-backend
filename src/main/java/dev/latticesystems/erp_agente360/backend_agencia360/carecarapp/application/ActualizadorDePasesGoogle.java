package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LlamadaHttp.RespuestaHttp;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mantiene al día el pase de Google Wallet de una cita (V2-M25.06): al cancelar
 * marca el objeto {@code EXPIRED} y al reprogramar le pone la fecha y el
 * horario nuevos. Corre después de confirmar la transacción y nunca la rompe:
 * un fallo de Google solo se registra, y sin credenciales no hace nada.
 *
 * <p>
 * Un 404 de Google es normal: el cliente nunca guardó ese pase.
 */
@Component
public class ActualizadorDePasesGoogle {

	static final String URL_TOKEN = "https://oauth2.googleapis.com/token";
	static final String URL_OBJETO = "https://walletobjects.googleapis.com/walletobjects/v1/genericObject/";
	private static final String ALCANCE = "https://www.googleapis.com/auth/wallet_object.issuer";
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	private static final Logger LOG = LoggerFactory.getLogger(ActualizadorDePasesGoogle.class);

	private final ConfiguracionWallet configuracion;
	private final LlamadaHttp http;
	private final ObjectMapper json = new ObjectMapper();

	@Autowired
	ActualizadorDePasesGoogle(ConfiguracionWallet configuracion, LlamadaHttp http) {
		this.configuracion = configuracion;
		this.http = http;
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void alCambiar(CambioDePaseDeCita cambio) {
		if (!configuracion.googleConfigurado()) {
			return;
		}
		try {
			String token = token();
			String id = configuracion.googleIssuerId() + ".carecar_cita_" + cambio.citaId();
			RespuestaHttp respuesta = http.enviar("PATCH", URL_OBJETO + id,
					Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"),
					json.writeValueAsString(cuerpo(cambio)));
			if (respuesta.codigo() >= 300 && respuesta.codigo() != 404) {
				LOG.warn("Google Wallet respondió {} al actualizar el pase de la cita {}", respuesta.codigo(),
						cambio.citaId());
			}
		} catch (Exception fallo) {
			LOG.warn("No se pudo actualizar el pase de Google Wallet de la cita {}", cambio.citaId(), fallo);
		}
	}

	private Map<String, Object> cuerpo(CambioDePaseDeCita cambio) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		if (cambio.tipo() == CambioDePaseDeCita.Tipo.CANCELADA) {
			cuerpo.put("state", "EXPIRED");
			return cuerpo;
		}
		cuerpo.put("validTimeInterval", Map.of("start", Map.of("date", cambio.inicio().format(ISO)), "end",
				Map.of("date", cambio.fin().format(ISO))));
		List<Map<String, String>> modulos = new ArrayList<>();
		modulos.add(Map.of("id", "inicio", "header", "Fecha y hora", "body",
				cambio.inicio().toLocalDate() + " " + cambio.inicio().toLocalTime()));
		if (cambio.vehiculoNombre() != null) {
			modulos.add(Map.of("id", "vehiculo", "header", "Vehículo", "body", cambio.vehiculoNombre()));
		}
		cuerpo.put("textModulesData", modulos);
		return cuerpo;
	}

	/** Token de acceso de la cuenta de servicio (JWT bearer de OAuth 2.0). */
	private String token() throws Exception {
		JsonNode cuenta = json.readTree(Base64.getDecoder().decode(configuracion.googleServiceAccountJsonBase64()));
		String pem = cuenta.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
		PrivateKey llave = KeyFactory.getInstance("RSA").generatePrivate(
				new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem.getBytes(StandardCharsets.US_ASCII))));
		Instant ahora = Instant.now();
		String asercion = Jwts.builder().issuer(cuenta.path("client_email").asText()).audience().add(URL_TOKEN).and()
				.claim("scope", ALCANCE).issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plusSeconds(3600)))
				.signWith(llave, Jwts.SIG.RS256).compact();
		RespuestaHttp respuesta = http.enviar("POST", URL_TOKEN,
				Map.of("Content-Type", "application/x-www-form-urlencoded"),
				"grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + asercion);
		if (respuesta.codigo() != 200) {
			throw new IllegalStateException("Google rechazó el token: " + respuesta.codigo());
		}
		return json.readTree(respuesta.cuerpo()).path("access_token").asText();
	}
}
