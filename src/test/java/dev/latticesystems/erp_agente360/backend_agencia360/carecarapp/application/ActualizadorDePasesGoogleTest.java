package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CambioDePaseDeCita.Tipo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LlamadaHttp.RespuestaHttp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActualizadorDePasesGoogleTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final ZonedDateTimeHelper HORA = new ZonedDateTimeHelper();

	/** Anota lo que se pide y responde como Google. */
	private static final class TransporteFalso implements LlamadaHttp {
		final List<String> peticiones = new ArrayList<>();
		final List<String> cuerpos = new ArrayList<>();
		final List<Map<String, String>> cabeceras = new ArrayList<>();
		int codigoDelParche = 200;

		@Override
		public RespuestaHttp enviar(String metodo, String url, Map<String, String> cabecera, String cuerpo) {
			peticiones.add(metodo + " " + url);
			cuerpos.add(cuerpo);
			cabeceras.add(cabecera);
			if (url.contains("oauth2.googleapis.com")) {
				return new RespuestaHttp(200, "{\"access_token\":\"token-de-prueba\"}");
			}
			return new RespuestaHttp(codigoDelParche, "{}");
		}
	}

	private TransporteFalso transporte;
	private ActualizadorDePasesGoogle actualizador;

	@BeforeEach
	void preparar() {
		transporte = new TransporteFalso();
		actualizador = new ActualizadorDePasesGoogle(CredencialesWalletDePrueba.nuevas().configuracion(), transporte);
	}

	@Test
	void cancelarMarcaElObjetoComoExpirado() throws Exception {
		actualizador.alCambiar(new CambioDePaseDeCita(42L, Tipo.CANCELADA, HORA.inicio(), HORA.fin(), "Versa"));

		assertThat(transporte.peticiones).hasSize(2);
		assertThat(transporte.peticiones.get(1)).isEqualTo("PATCH https://walletobjects.googleapis.com/walletobjects"
				+ "/v1/genericObject/" + CredencialesWalletDePrueba.ISSUER_ID + ".carecar_cita_42");
		assertThat(transporte.cabeceras.get(1)).containsEntry("Authorization", "Bearer token-de-prueba");
		JsonNode cuerpo = JSON.readTree(transporte.cuerpos.get(1));
		assertThat(cuerpo.path("state").asText()).isEqualTo("EXPIRED");
	}

	@Test
	void reprogramarActualizaLaFechaYElHorario() throws Exception {
		actualizador.alCambiar(new CambioDePaseDeCita(42L, Tipo.REPROGRAMADA, HORA.inicio(), HORA.fin(), "Versa"));

		JsonNode cuerpo = JSON.readTree(transporte.cuerpos.get(1));
		assertThat(cuerpo.has("state")).isFalse();
		assertThat(cuerpo.at("/validTimeInterval/start/date").asText()).startsWith("2026-12-03T09:00");
		assertThat(cuerpo.at("/validTimeInterval/end/date").asText()).startsWith("2026-12-03T09:30");
		assertThat(cuerpo.path("textModulesData")).hasSize(2);
		assertThat(cuerpo.at("/textModulesData/0/body").asText()).isEqualTo("2026-12-03 09:00");
	}

	@Test
	void unPaseQueNuncaSeGuardoNoEsError() {
		transporte.codigoDelParche = 404;
		actualizador.alCambiar(new CambioDePaseDeCita(42L, Tipo.CANCELADA, HORA.inicio(), HORA.fin(), null));
		assertThat(transporte.peticiones).hasSize(2);
	}

	@Test
	void unFalloDeGoogleNuncaRompeLaCancelacion() {
		transporte.codigoDelParche = 500;
		actualizador.alCambiar(new CambioDePaseDeCita(42L, Tipo.CANCELADA, HORA.inicio(), HORA.fin(), null));
	}

	@Test
	void sinCredencialesDeGoogleNoLlamaANadie() {
		ConfiguracionWallet vacia = new ConfiguracionWallet("", "", "", "", "", "", "");
		new ActualizadorDePasesGoogle(vacia, transporte)
				.alCambiar(new CambioDePaseDeCita(42L, Tipo.CANCELADA, HORA.inicio(), HORA.fin(), null));
		assertThat(transporte.peticiones).isEmpty();
	}

	/** Hora fija de la cita de prueba. */
	private static final class ZonedDateTimeHelper {
		java.time.ZonedDateTime inicio() {
			return java.time.ZonedDateTime.of(2026, 12, 3, 9, 0, 0, 0, java.time.ZoneId.of("America/Mexico_City"));
		}

		java.time.ZonedDateTime fin() {
			return inicio().plusMinutes(30);
		}
	}
}
