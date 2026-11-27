package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure.LlamadaHttp.RespuestaHttp;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link EnvioPush} con FCM HTTP v1 (V2-M06.07). El token de acceso sale de la
 * cuenta de servicio con el mismo JWT bearer de OAuth 2.0 que usa
 * {@code ActualizadorDePasesGoogle}, y se reutiliza hasta unos minutos antes de
 * vencer.
 *
 * <p>
 * Nunca lanza: cualquier fallo se vuelve {@link ResultadoEnvio#FALLO}, y sin
 * credenciales ni siquiera se llama a la red. Solo un 404 (o un
 * {@code UNREGISTERED}) significa que el token del teléfono ya no existe; un
 * 400 es un problema de la solicitud, no del dispositivo, y no lo desactiva.
 */
@Component
public class EnvioPushFcm implements EnvioPush {

	static final String URL_TOKEN = "https://oauth2.googleapis.com/token";
	static final String URL_ENVIO = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
	private static final String ALCANCE = "https://www.googleapis.com/auth/firebase.messaging";
	private static final Duration VIGENCIA_DE_LA_ASERCION = Duration.ofHours(1);
	private static final Duration MARGEN = Duration.ofMinutes(5);
	private static final Logger LOG = LoggerFactory.getLogger(EnvioPushFcm.class);

	private final ConfiguracionPush configuracion;
	private final LlamadaHttp http;
	private final Clock reloj;
	private final ObjectMapper json = new ObjectMapper();

	private String tokenDeAcceso;
	private Instant tokenVenceEn = Instant.EPOCH;

	@Autowired
	EnvioPushFcm(ConfiguracionPush configuracion, LlamadaHttp http) {
		this(configuracion, http, Clock.systemUTC());
	}

	EnvioPushFcm(ConfiguracionPush configuracion, LlamadaHttp http, Clock reloj) {
		this.configuracion = configuracion;
		this.http = http;
		this.reloj = reloj;
	}

	@Override
	public ResultadoEnvio enviar(MensajePush mensaje) {
		if (!configuracion.configurado()) {
			return ResultadoEnvio.NO_CONFIGURADO;
		}
		try {
			RespuestaHttp respuesta = http.enviar("POST", URL_ENVIO.formatted(configuracion.proyectoId()),
					Map.of("Authorization", "Bearer " + tokenDeAcceso(), "Content-Type", "application/json"),
					json.writeValueAsString(cuerpo(mensaje)));
			return interpretar(respuesta);
		} catch (Exception fallo) {
			LOG.warn("No se pudo mandar el push por FCM", fallo);
			return ResultadoEnvio.FALLO;
		}
	}

	private ResultadoEnvio interpretar(RespuestaHttp respuesta) {
		int codigo = respuesta.codigo();
		if (codigo >= 200 && codigo < 300) {
			return ResultadoEnvio.ENVIADO;
		}
		if (codigo == 404 || (respuesta.cuerpo() != null && respuesta.cuerpo().contains("UNREGISTERED"))) {
			return ResultadoEnvio.TOKEN_INVALIDO;
		}
		if (codigo == 401) {
			// Google ya no acepta el token de acceso: el siguiente envío pide otro.
			invalidarTokenDeAcceso();
		}
		LOG.warn("FCM respondió {} al mandar un push", codigo);
		return ResultadoEnvio.FALLO;
	}

	private Map<String, Object> cuerpo(MensajePush mensaje) {
		Map<String, Object> contenido = new LinkedHashMap<>();
		contenido.put("token", mensaje.token());
		contenido.put("notification", Map.of("title", mensaje.titulo(), "body", mensaje.cuerpo()));
		contenido.put("data", mensaje.datos());
		contenido.put("android", Map.of("priority", "HIGH"));
		contenido.put("apns",
				Map.of("headers", Map.of("apns-priority", "10"), "payload", Map.of("aps", Map.of("sound", "default"))));
		return Map.of("message", contenido);
	}

	private synchronized String tokenDeAcceso() throws Exception {
		Instant ahora = reloj.instant();
		if (tokenDeAcceso != null && ahora.isBefore(tokenVenceEn.minus(MARGEN))) {
			return tokenDeAcceso;
		}
		JsonNode cuenta = json.readTree(Base64.getDecoder().decode(configuracion.cuentaDeServicioBase64()));
		String pem = cuenta.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
		PrivateKey llave = KeyFactory.getInstance("RSA").generatePrivate(
				new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem.getBytes(StandardCharsets.US_ASCII))));
		String asercion = Jwts.builder().issuer(cuenta.path("client_email").asText()).audience().add(URL_TOKEN).and()
				.claim("scope", ALCANCE).issuedAt(Date.from(ahora))
				.expiration(Date.from(ahora.plus(VIGENCIA_DE_LA_ASERCION))).signWith(llave, Jwts.SIG.RS256).compact();
		RespuestaHttp respuesta = http.enviar("POST", URL_TOKEN,
				Map.of("Content-Type", "application/x-www-form-urlencoded"),
				"grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + asercion);
		if (respuesta.codigo() != 200) {
			throw new IllegalStateException("Google rechazó el token: " + respuesta.codigo());
		}
		JsonNode cuerpo = json.readTree(respuesta.cuerpo());
		tokenDeAcceso = cuerpo.path("access_token").asText();
		tokenVenceEn = ahora.plusSeconds(cuerpo.path("expires_in").asLong(VIGENCIA_DE_LA_ASERCION.toSeconds()));
		return tokenDeAcceso;
	}

	private synchronized void invalidarTokenDeAcceso() {
		tokenDeAcceso = null;
		tokenVenceEn = Instant.EPOCH;
	}
}
