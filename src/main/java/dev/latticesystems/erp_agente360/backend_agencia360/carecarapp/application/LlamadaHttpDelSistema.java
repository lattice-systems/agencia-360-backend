package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link LlamadaHttp} con el cliente HTTP del JDK y tiempos de espera cortos.
 */
@Component
class LlamadaHttpDelSistema implements LlamadaHttp {

	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@Override
	public RespuestaHttp enviar(String metodo, String url, Map<String, String> cabeceras, String cuerpo) {
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
				.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
		cabeceras.forEach(peticion::header);
		try {
			HttpResponse<String> respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
			return new RespuestaHttp(respuesta.statusCode(), respuesta.body());
		} catch (IOException ex) {
			throw new IllegalStateException("No se pudo llamar a " + url, ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Llamada interrumpida a " + url, ex);
		}
	}
}
