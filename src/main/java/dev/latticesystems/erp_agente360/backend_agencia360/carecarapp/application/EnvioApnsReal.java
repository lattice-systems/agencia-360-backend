package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LlamadaHttp.RespuestaHttp;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link EnvioApns} contra APNs con autenticación por token: un JWT ES256
 * firmado con la llave {@code .p8} del equipo. APNs exige HTTP/2; el cliente
 * del JDK que usa {@link LlamadaHttpDelSistema} lo negocia por ALPN, así que se
 * reutiliza la misma salida HTTP en lugar de agregar una librería.
 *
 * <p>
 * Apple rechaza tokens de más de una hora y limita cuántas veces se renueva,
 * por eso el token se reutiliza cuarenta minutos. Sin credenciales no hace
 * nada. Está sin probar contra Apple: solo contra un transporte falso.
 */
@Component
class EnvioApnsReal implements EnvioApns {

	static final String PRODUCCION = "https://api.push.apple.com/3/device/";
	static final String SANDBOX = "https://api.sandbox.push.apple.com/3/device/";
	private static final Duration VIGENCIA_DEL_TOKEN = Duration.ofMinutes(40);
	private static final Logger LOG = LoggerFactory.getLogger(EnvioApnsReal.class);

	private final ConfiguracionApns configuracion;
	private final LlamadaHttp http;
	private final Clock reloj;
	private final ObjectMapper json = new ObjectMapper();

	private String tokenVigente;
	private Instant tokenEmitidoEn;

	@Autowired
	EnvioApnsReal(ConfiguracionApns configuracion, LlamadaHttp http) {
		this(configuracion, http, Clock.systemUTC());
	}

	EnvioApnsReal(ConfiguracionApns configuracion, LlamadaHttp http, Clock reloj) {
		this.configuracion = configuracion;
		this.http = http;
		this.reloj = reloj;
	}

	@Override
	public boolean disponible() {
		return configuracion.configurado();
	}

	@Override
	public ResultadoApns enviar(PushApns push) {
		if (!disponible()) {
			return ResultadoApns.NO_CONFIGURADO;
		}
		try {
			Map<String, String> cabeceras = Map.of("authorization", "bearer " + token(), "apns-topic", push.topic(),
					"apns-push-type", push.tipo().encabezado(), "apns-priority", push.tipo().prioridad(),
					"content-type", "application/json");
			String url = (push.sandbox() ? SANDBOX : PRODUCCION) + push.token();
			RespuestaHttp respuesta = http.enviar("POST", url, cabeceras, push.carga());
			ResultadoApns resultado = new ResultadoApns(respuesta.codigo(), motivo(respuesta.cuerpo()));
			if (!resultado.aceptado()) {
				LOG.warn("APNs rechazó un push {}: {} {}", push.tipo(), resultado.codigo(), resultado.motivo());
			}
			return resultado;
		} catch (GeneralSecurityException | IllegalArgumentException | IllegalStateException fallo) {
			LOG.warn("No se pudo mandar el push {} a APNs", push.tipo(), fallo);
			return new ResultadoApns(0, "Fallo");
		}
	}

	private String motivo(String cuerpo) {
		if (cuerpo == null || cuerpo.isBlank()) {
			return "";
		}
		try {
			JsonNode raiz = json.readTree(cuerpo);
			return raiz.path("reason").asText("");
		} catch (java.io.IOException noEsJson) {
			return "";
		}
	}

	private synchronized String token() throws GeneralSecurityException {
		Instant ahora = reloj.instant();
		if (tokenVigente == null || tokenEmitidoEn.plus(VIGENCIA_DEL_TOKEN).isBefore(ahora)) {
			PrivateKey llave = llaveP8();
			tokenVigente = Jwts.builder().header().keyId(configuracion.keyId()).and().issuer(configuracion.teamId())
					.issuedAt(Date.from(ahora)).signWith(llave, Jwts.SIG.ES256).compact();
			tokenEmitidoEn = ahora;
		}
		return tokenVigente;
	}

	private PrivateKey llaveP8() throws GeneralSecurityException {
		String pem = new String(Base64.getDecoder().decode(configuracion.keyP8Base64()), StandardCharsets.UTF_8);
		String cuerpo = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		return KeyFactory.getInstance("EC")
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(cuerpo)));
	}
}
