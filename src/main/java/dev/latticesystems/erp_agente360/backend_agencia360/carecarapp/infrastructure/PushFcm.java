package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Firebase Cloud Messaging por su API HTTP v1, con la cuenta de servicio que
 * llega por variable de entorno. Un token que FCM ya no reconoce (404, o 400
 * con UNREGISTERED) se regresa para borrarlo; cualquier otro error solo se
 * registra: un aviso que no llega no debe tumbar la operación que lo causó.
 */
class PushFcm implements EnvioPush {

	static final String ALCANCE = "https://www.googleapis.com/auth/firebase.messaging";
	private static final Logger LOG = LoggerFactory.getLogger(PushFcm.class);

	private final GoogleCredentials credenciales;
	private final RestClient cliente;
	private final String ruta;

	PushFcm(GoogleCredentials credenciales, String proyecto) {
		this.credenciales = credenciales.createScoped(List.of(ALCANCE));
		this.cliente = RestClient.builder().baseUrl("https://fcm.googleapis.com").build();
		this.ruta = "/v1/projects/" + proyecto + "/messages:send";
	}

	@Override
	public Set<String> enviar(List<String> tokens, Notificacion notificacion) {
		String acceso;
		try {
			credenciales.refreshIfExpired();
			acceso = credenciales.getAccessToken().getTokenValue();
		} catch (IOException e) {
			LOG.warn("No se pudo autenticar con Firebase: {}", e.getMessage());
			return Set.of();
		}
		Set<String> vencidos = new HashSet<>();
		for (String token : tokens) {
			Map<String, Object> mensaje = Map.of("message",
					Map.of("token", token, "notification",
							Map.of("title", notificacion.titulo(), "body", notificacion.cuerpo()), "data",
							notificacion.datos()));
			try {
				cliente.post().uri(ruta).header("Authorization", "Bearer " + acceso)
						.contentType(MediaType.APPLICATION_JSON).body(mensaje).retrieve().toBodilessEntity();
			} catch (RestClientResponseException e) {
				if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
						|| e.getResponseBodyAsString().contains("UNREGISTERED")) {
					vencidos.add(token);
				} else {
					LOG.warn("Firebase rechazó un push: {}", e.getStatusCode());
				}
			} catch (RestClientException e) {
				LOG.warn("No se pudo mandar un push: {}", e.getMessage());
			}
		}
		return vencidos;
	}
}
