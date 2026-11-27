package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.MensajePush;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.ResultadoEnvio;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure.LlamadaHttp.RespuestaHttp;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2-M06.07: FCM HTTP v1 con token OAuth de cuenta de servicio, probado sin red
 * ni credenciales reales (las llaves se generan en cada prueba, R8).
 */
class EnvioPushFcmTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String PROYECTO = "carecar-prueba";
	private static final String CORREO_CUENTA = "push@carecar-prueba.iam.gserviceaccount.com";
	private static final String URL_ENVIO = "https://fcm.googleapis.com/v1/projects/" + PROYECTO + "/messages:send";

	/** Anota lo que se pide y responde como Google. */
	private static final class TransporteFalso implements LlamadaHttp {
		final List<String> peticiones = new ArrayList<>();
		final List<String> cuerpos = new ArrayList<>();
		final List<Map<String, String>> cabeceras = new ArrayList<>();
		int codigoDelEnvio = 200;
		String cuerpoDelEnvio = "{\"name\":\"projects/carecar-prueba/messages/1\"}";
		boolean fallaLaRed;

		@Override
		public RespuestaHttp enviar(String metodo, String url, Map<String, String> cabecera, String cuerpo) {
			peticiones.add(metodo + " " + url);
			cuerpos.add(cuerpo);
			cabeceras.add(cabecera);
			if (fallaLaRed) {
				throw new IllegalStateException("sin red");
			}
			if (url.contains("oauth2.googleapis.com")) {
				return new RespuestaHttp(200, "{\"access_token\":\"token-de-prueba\",\"expires_in\":3600}");
			}
			return new RespuestaHttp(codigoDelEnvio, cuerpoDelEnvio);
		}

		long llamadasDeToken() {
			return peticiones.stream().filter(peticion -> peticion.contains("oauth2.googleapis.com")).count();
		}
	}

	private static final class RelojMovil extends Clock {
		private Instant ahora = Instant.parse("2026-12-01T10:00:00Z");

		void avanzar(Duration tiempo) {
			ahora = ahora.plus(tiempo);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zona) {
			return this;
		}

		@Override
		public Instant instant() {
			return ahora;
		}
	}

	private static final MensajePush MENSAJE = new MensajePush("token-del-telefono", "Tu auto está listo",
			"Ya puedes pasar por él.", Map.of("evento", "orden", "destino", "/taller/12", "ordenId", "12"));

	private TransporteFalso transporte;
	private RelojMovil reloj;
	private KeyPair llaves;
	private EnvioPushFcm envio;

	@BeforeEach
	void preparar() throws Exception {
		transporte = new TransporteFalso();
		reloj = new RelojMovil();
		KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
		generador.initialize(2048);
		llaves = generador.generateKeyPair();
		envio = new EnvioPushFcm(configuracion(llaves), transporte, reloj);
	}

	private ConfiguracionPush configuracion(KeyPair pareja) throws Exception {
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
						.encodeToString(pareja.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		String cuenta = JSON.writeValueAsString(Map.of("client_email", CORREO_CUENTA, "private_key", pem));
		return new ConfiguracionPush(PROYECTO,
				Base64.getEncoder().encodeToString(cuenta.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void mandaElPushPorLaApiV1ConElTokenDeLaCuentaDeServicio() throws Exception {
		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.ENVIADO);

		assertThat(transporte.peticiones).containsExactly("POST https://oauth2.googleapis.com/token",
				"POST " + URL_ENVIO);
		assertThat(transporte.cabeceras.get(1)).containsEntry("Authorization", "Bearer token-de-prueba")
				.containsEntry("Content-Type", "application/json");
		JsonNode mensaje = JSON.readTree(transporte.cuerpos.get(1)).path("message");
		assertThat(mensaje.path("token").asText()).isEqualTo("token-del-telefono");
		assertThat(mensaje.at("/notification/title").asText()).isEqualTo("Tu auto está listo");
		assertThat(mensaje.at("/notification/body").asText()).isEqualTo("Ya puedes pasar por él.");
		assertThat(mensaje.at("/data/destino").asText()).isEqualTo("/taller/12");
		assertThat(mensaje.at("/data/ordenId").asText()).isEqualTo("12");
		assertThat(mensaje.at("/android/priority").asText()).isEqualTo("HIGH");
	}

	@Test
	void laAserciónDelTokenVaFirmadaPorLaCuentaConElAlcanceDeFirebase() {
		envio.enviar(MENSAJE);

		String asercion = transporte.cuerpos.get(0).substring(transporte.cuerpos.get(0).indexOf("assertion=") + 10);
		var claims = Jwts.parser().verifyWith(llaves.getPublic()).clockSkewSeconds(86_400).build()
				.parseSignedClaims(asercion).getPayload();
		assertThat(claims.getIssuer()).isEqualTo(CORREO_CUENTA);
		assertThat(claims.get("scope", String.class)).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
		assertThat(claims.getAudience()).containsExactly("https://oauth2.googleapis.com/token");
	}

	@Test
	void reusaElTokenDeAccesoMientrasVigente() {
		envio.enviar(MENSAJE);
		envio.enviar(MENSAJE);

		assertThat(transporte.llamadasDeToken()).isEqualTo(1);
	}

	@Test
	void pideOtroTokenCuandoElAnteriorEstaPorVencer() {
		envio.enviar(MENSAJE);
		reloj.avanzar(Duration.ofMinutes(58));
		envio.enviar(MENSAJE);

		assertThat(transporte.llamadasDeToken()).isEqualTo(2);
	}

	@Test
	void unTokenQueFcmNoConoceSeReportaComoInvalido() {
		transporte.codigoDelEnvio = 404;
		String detalle = "{\"errorCode\":\"UNREGISTERED\"}";
		transporte.cuerpoDelEnvio = "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\",\"details\":[" + detalle + "]}}";

		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.TOKEN_INVALIDO);
	}

	@Test
	void unaRespuestaDeErrorDelServidorEsUnFalloNoUnTokenInvalido() {
		transporte.codigoDelEnvio = 500;
		transporte.cuerpoDelEnvio = "{}";

		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.FALLO);
	}

	@Test
	void unaSolicitudRechazadaPorFormatoNoDesactivaElDispositivo() {
		transporte.codigoDelEnvio = 400;
		transporte.cuerpoDelEnvio = "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}";

		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.FALLO);
	}

	@Test
	void siGoogleRechazaElTokenDeAccesoSePideOtroEnElSiguienteEnvio() {
		transporte.codigoDelEnvio = 401;
		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.FALLO);

		transporte.codigoDelEnvio = 200;
		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.ENVIADO);
		assertThat(transporte.llamadasDeToken()).isEqualTo(2);
	}

	@Test
	void unFalloDeRedNuncaSePropaga() {
		transporte.fallaLaRed = true;

		assertThat(envio.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.FALLO);
	}

	@Test
	void unaCuentaDeServicioRotaNuncaSePropaga() {
		EnvioPushFcm roto = new EnvioPushFcm(new ConfiguracionPush(PROYECTO, "bm8gZXMganNvbg=="), transporte, reloj);

		assertThat(roto.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.FALLO);
		assertThat(transporte.peticiones).isEmpty();
	}

	@Test
	void sinCredencialesNoLlamaANadie() {
		EnvioPushFcm sinCredenciales = new EnvioPushFcm(new ConfiguracionPush("", ""), transporte, reloj);

		assertThat(sinCredenciales.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.NO_CONFIGURADO);
		assertThat(transporte.peticiones).isEmpty();
	}

	@Test
	void conSoloUnaDeLasDosCredencialesTampocoLlama() {
		EnvioPushFcm incompleto = new EnvioPushFcm(new ConfiguracionPush(PROYECTO, ""), transporte, reloj);

		assertThat(incompleto.enviar(MENSAJE)).isEqualTo(ResultadoEnvio.NO_CONFIGURADO);
		assertThat(transporte.peticiones).isEmpty();
	}
}
