package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.PushApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.ResultadoApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LlamadaHttp.RespuestaHttp;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvioApnsRealTest {

	private static final String KEY_ID = "KEY1234567";
	private static final String TEAM_ID = "ABCDE12345";

	/** Anota lo que se manda a Apple y responde lo que se le diga. */
	private static final class TransporteFalso implements LlamadaHttp {
		final List<String> peticiones = new ArrayList<>();
		final List<Map<String, String>> cabeceras = new ArrayList<>();
		final List<String> cuerpos = new ArrayList<>();
		RespuestaHttp respuesta = new RespuestaHttp(200, "");
		RuntimeException fallo;

		@Override
		public RespuestaHttp enviar(String metodo, String url, Map<String, String> cabecera, String cuerpo) {
			if (fallo != null) {
				throw fallo;
			}
			peticiones.add(metodo + " " + url);
			cabeceras.add(cabecera);
			cuerpos.add(cuerpo);
			return respuesta;
		}
	}

	private KeyPair llaves;
	private TransporteFalso transporte;
	private Clock reloj;
	private EnvioApnsReal envio;

	@BeforeEach
	void preparar() throws Exception {
		KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
		generador.initialize(new ECGenParameterSpec("secp256r1"));
		llaves = generador.generateKeyPair();
		String p8 = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
						.encodeToString(llaves.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		ConfiguracionApns configuracion = new ConfiguracionApns(KEY_ID, TEAM_ID,
				Base64.getEncoder().encodeToString(p8.getBytes(StandardCharsets.UTF_8)), "mx.carecar.app", true);
		transporte = new TransporteFalso();
		reloj = Clock.fixed(Instant.parse("2026-12-01T15:00:00Z"), ZoneOffset.UTC);
		envio = new EnvioApnsReal(configuracion, transporte, reloj);
	}

	@Test
	void elPushDeUnPaseEsVacioYVaALaProduccionDeApple() {
		ResultadoApns resultado = envio.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado.aceptado()).isTrue();
		assertThat(transporte.peticiones).containsExactly("POST https://api.push.apple.com/3/device/abc123");
		Map<String, String> cabecera = transporte.cabeceras.getFirst();
		assertThat(cabecera).containsEntry("apns-topic", "pass.mx.carecar.cita")
				.containsEntry("apns-push-type", "background").containsEntry("apns-priority", "5");
		assertThat(transporte.cuerpos.getFirst()).isEqualTo("{}");
	}

	@Test
	void elPushDeUnaActividadUsaElTemaDeLiveActivityYElSandboxConfigurado() {
		String carga = "{\"aps\":{\"event\":\"update\"}}";

		envio.enviar(PushApns.deUnaActividad("tokenAct", "mx.carecar.app", true, carga));

		assertThat(transporte.peticiones).containsExactly("POST https://api.sandbox.push.apple.com/3/device/tokenAct");
		Map<String, String> cabecera = transporte.cabeceras.getFirst();
		assertThat(cabecera).containsEntry("apns-topic", "mx.carecar.app.push-type.liveactivity")
				.containsEntry("apns-push-type", "liveactivity").containsEntry("apns-priority", "10");
		assertThat(transporte.cuerpos.getFirst()).isEqualTo(carga);
	}

	@Test
	void laAutenticacionEsUnJwtEs256FirmadoConLaLlaveDelEquipo() {
		envio.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		String autorizacion = transporte.cabeceras.getFirst().get("authorization");
		assertThat(autorizacion).startsWith("bearer ");
		Jws<Claims> jwt = Jwts.parser().verifyWith(llaves.getPublic()).build()
				.parseSignedClaims(autorizacion.substring("bearer ".length()));
		assertThat(jwt.getHeader().getKeyId()).isEqualTo(KEY_ID);
		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("ES256");
		assertThat(jwt.getPayload().getIssuer()).isEqualTo(TEAM_ID);
		assertThat(jwt.getPayload().getIssuedAt().toInstant()).isEqualTo(reloj.instant());
	}

	@Test
	void elTokenSeReutilizaEntreEnviosParaNoPasarseDelLimiteDeApple() {
		envio.enviar(PushApns.deUnPase("uno", "pass.mx.carecar.cita"));
		envio.enviar(PushApns.deUnPase("dos", "pass.mx.carecar.cita"));

		assertThat(transporte.cabeceras.get(0).get("authorization"))
				.isEqualTo(transporte.cabeceras.get(1).get("authorization"));
	}

	@Test
	void unTokenDeDispositivoMuertoSeReconoceParaBorrarlo() {
		transporte.respuesta = new RespuestaHttp(410, "{\"reason\":\"Unregistered\"}");

		ResultadoApns resultado = envio.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado.aceptado()).isFalse();
		assertThat(resultado.codigo()).isEqualTo(410);
		assertThat(resultado.motivo()).isEqualTo("Unregistered");
		assertThat(resultado.tokenMuerto()).isTrue();
	}

	@Test
	void unErrorQueNoEsDelTokenNoLoMarcaComoMuerto() {
		transporte.respuesta = new RespuestaHttp(429, "{\"reason\":\"TooManyRequests\"}");

		ResultadoApns resultado = envio.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado.tokenMuerto()).isFalse();
	}

	@Test
	void unaRespuestaSinJsonNoRompeLaLectura() {
		transporte.respuesta = new RespuestaHttp(500, "<html>caído</html>");

		ResultadoApns resultado = envio.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado.codigo()).isEqualTo(500);
		assertThat(resultado.motivo()).isEmpty();
	}

	@Test
	void unaCaidaDeRedDevuelveUnResultadoEnLugarDeLanzar() {
		transporte.fallo = new IllegalStateException("sin red");

		ResultadoApns resultado = envio.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado.aceptado()).isFalse();
		assertThat(resultado.codigo()).isZero();
		assertThat(resultado.tokenMuerto()).isFalse();
	}

	@Test
	void sinCredencialesNoDisponibleYNoLlamaANadie() {
		EnvioApnsReal sinLlave = new EnvioApnsReal(new ConfiguracionApns("", "", "", "", false), transporte, reloj);

		assertThat(sinLlave.disponible()).isFalse();
		ResultadoApns resultado = sinLlave.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado).isEqualTo(ResultadoApns.NO_CONFIGURADO);
		assertThat(transporte.peticiones).isEmpty();
	}

	@Test
	void unaLlaveIlegibleSeReportaSinLanzar() {
		ConfiguracionApns rota = new ConfiguracionApns(KEY_ID, TEAM_ID,
				Base64.getEncoder().encodeToString("no es una llave".getBytes(StandardCharsets.UTF_8)), "", false);

		ResultadoApns resultado = new EnvioApnsReal(rota, transporte, reloj)
				.enviar(PushApns.deUnPase("abc123", "pass.mx.carecar.cita"));

		assertThat(resultado.aceptado()).isFalse();
		assertThat(transporte.peticiones).isEmpty();
	}
}
