package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProveedorIA;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProveedorIANoDisponibleException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini por su API REST (ADR 0007), en el nivel gratis: el proyecto de Google
 * no tiene facturación, así que una cuota agotada falla en vez de cobrarse.
 * Espera hasta 20 s y reintenta una vez los errores de red y los 5xx.
 */
public class GeminiProveedorIA implements ProveedorIA {

	private static final Logger LOG = LoggerFactory.getLogger(GeminiProveedorIA.class);
	private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
	private static final Duration ESPERA = Duration.ofSeconds(20);

	private final RestClient cliente;
	private final ObjectMapper json;
	private final String modeloTexto;
	private final String modeloEmbedding;

	public GeminiProveedorIA(String llave, String modeloTexto, String modeloEmbedding, ObjectMapper json) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(ESPERA);
		fabrica.setReadTimeout(ESPERA);
		this.cliente = RestClient.builder().baseUrl(BASE).requestFactory(fabrica).defaultHeader("x-goog-api-key", llave)
				.build();
		this.json = json;
		this.modeloTexto = modeloTexto;
		this.modeloEmbedding = modeloEmbedding;
	}

	@Override
	public String nombre() {
		return "gemini";
	}

	@Override
	public Optional<float[]> embeber(String texto) {
		Map<String, Object> cuerpo = Map.of("content", Map.of("parts", List.of(Map.of("text", texto))),
				"outputDimensionality", DIMENSION);
		JsonNode respuesta = llamar("/models/" + modeloEmbedding + ":embedContent", cuerpo);
		JsonNode valores = respuesta.path("embedding").path("values");
		if (!valores.isArray() || valores.isEmpty()) {
			throw new ProveedorIANoDisponibleException("Gemini no devolvió el vector");
		}
		return Optional.of(normalizar(valores));
	}

	@Override
	public RespuestaIA responder(String instrucciones, String contenido) {
		Map<String, Object> esquema = Map.of("type", "OBJECT", "properties",
				Map.of("respuesta", Map.of("type", "STRING"), "encontrado", Map.of("type", "BOOLEAN"), "paginas",
						Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER"))),
				"required", List.of("respuesta", "encontrado", "paginas"));
		Map<String, Object> cuerpo = Map.of("systemInstruction",
				Map.of("parts", List.of(Map.of("text", instrucciones))), "contents",
				List.of(Map.of("role", "user", "parts", List.of(Map.of("text", contenido)))), "generationConfig",
				Map.of("temperature", 0.2, "responseMimeType", MediaType.APPLICATION_JSON_VALUE, "responseSchema",
						esquema));
		JsonNode respuesta = llamar("/models/" + modeloTexto + ":generateContent", cuerpo);
		String texto = respuesta.path("candidates").path(0).path("content").path("parts").path(0).path("text")
				.asText("");
		try {
			JsonNode datos = json.readTree(texto);
			List<Integer> paginas = new ArrayList<>();
			datos.path("paginas").forEach(pagina -> paginas.add(pagina.asInt()));
			String respuestaTexto = datos.path("respuesta").asText("").strip();
			if (respuestaTexto.isEmpty()) {
				throw new ProveedorIANoDisponibleException("Gemini respondió vacío");
			}
			return new RespuestaIA(respuestaTexto, datos.path("encontrado").asBoolean(false), paginas);
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new ProveedorIANoDisponibleException("Gemini no respondió en el formato pedido", ex);
		}
	}

	@Override
	public RespuestaTestigo identificarTestigo(String instrucciones, byte[] jpeg) {
		Map<String, Object> esquema = Map.of("type", "OBJECT", "properties",
				Map.of("reconocido", Map.of("type", "BOOLEAN"), "testigo", Map.of("type", "STRING"), "gravedad",
						Map.of("type", "STRING", "enum", List.of("roja", "amarilla", "informativa")), "queHacer",
						Map.of("type", "STRING")),
				"required", List.of("reconocido"));
		Map<String, Object> imagen = Map.of("inline_data",
				Map.of("mime_type", "image/jpeg", "data", java.util.Base64.getEncoder().encodeToString(jpeg)));
		Map<String, Object> cuerpo = Map.of("systemInstruction",
				Map.of("parts", List.of(Map.of("text", instrucciones))), "contents",
				List.of(Map.of("role", "user", "parts",
						List.of(imagen, Map.of("text", "¿Qué testigo del tablero muestra la foto?")))),
				"generationConfig", Map.of("temperature", 0.1, "responseMimeType", MediaType.APPLICATION_JSON_VALUE,
						"responseSchema", esquema));
		JsonNode respuesta = llamar("/models/" + modeloTexto + ":generateContent", cuerpo);
		String texto = respuesta.path("candidates").path(0).path("content").path("parts").path(0).path("text")
				.asText("");
		try {
			JsonNode datos = json.readTree(texto);
			if (!datos.path("reconocido").asBoolean(false)) {
				return new RespuestaTestigo(false, null, null, null);
			}
			String gravedad = datos.path("gravedad").asText("informativa");
			if (!List.of("roja", "amarilla", "informativa").contains(gravedad)) {
				gravedad = "informativa";
			}
			return new RespuestaTestigo(true, datos.path("testigo").asText("").strip(), gravedad,
					datos.path("queHacer").asText("").strip());
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new ProveedorIANoDisponibleException("Gemini no respondió en el formato pedido", ex);
		}
	}

	@Override
	public void responderEnFlujo(String instrucciones, String contenido, java.util.function.Consumer<String> alTrozo) {
		Map<String, Object> cuerpo = Map.of("systemInstruction",
				Map.of("parts", List.of(Map.of("text", instrucciones))), "contents",
				List.of(Map.of("role", "user", "parts", List.of(Map.of("text", contenido)))), "generationConfig",
				Map.of("temperature", 0.2));
		try {
			cliente.post().uri("/models/" + modeloTexto + ":streamGenerateContent?alt=sse")
					.contentType(MediaType.APPLICATION_JSON).body(cuerpo).exchange((peticion, respuesta) -> {
						if (respuesta.getStatusCode().isError()) {
							int estado = respuesta.getStatusCode().value();
							LOG.warn("Gemini respondió {} en el flujo", estado);
							throw new ProveedorIANoDisponibleException(estado == 429
									? "Se agotó la cuota gratis de Gemini"
									: "Gemini respondió " + estado);
						}
						leerFlujo(respuesta.getBody(), alTrozo);
						return null;
					});
		} catch (ResourceAccessException ex) {
			LOG.warn("Gemini no respondió a tiempo en el flujo");
			throw new ProveedorIANoDisponibleException("Gemini no respondió a tiempo", ex);
		}
	}

	/** Lee el SSE de Gemini y manda el texto de cada evento. */
	private void leerFlujo(java.io.InputStream flujo, java.util.function.Consumer<String> alTrozo)
			throws java.io.IOException {
		try (java.io.BufferedReader lector = new java.io.BufferedReader(
				new java.io.InputStreamReader(flujo, java.nio.charset.StandardCharsets.UTF_8))) {
			String linea;
			while ((linea = lector.readLine()) != null) {
				if (!linea.startsWith("data:")) {
					continue;
				}
				JsonNode partes = json.readTree(linea.substring(5).strip()).path("candidates").path(0).path("content")
						.path("parts");
				String trozo = partes.path(0).path("text").asText("");
				if (!trozo.isEmpty()) {
					alTrozo.accept(trozo);
				}
			}
		}
	}

	private JsonNode llamar(String ruta, Map<String, Object> cuerpo) {
		for (int intento = 1;; intento++) {
			try {
				return cliente.post().uri(ruta).contentType(MediaType.APPLICATION_JSON).body(cuerpo).retrieve()
						.body(JsonNode.class);
			} catch (RestClientResponseException ex) {
				int estado = ex.getStatusCode().value();
				LOG.warn("Gemini respondió {} en {}", estado, ruta);
				if (estado >= 500 && intento == 1) {
					continue;
				}
				throw new ProveedorIANoDisponibleException(
						estado == 429 ? "Se agotó la cuota gratis de Gemini" : "Gemini respondió " + estado, ex);
			} catch (ResourceAccessException ex) {
				LOG.warn("Gemini no respondió a tiempo en {}", ruta);
				if (intento == 1) {
					continue;
				}
				throw new ProveedorIANoDisponibleException("Gemini no respondió a tiempo", ex);
			}
		}
	}

	/** Recorta a la dimensión de la tabla y deja el vector con largo 1. */
	private static float[] normalizar(JsonNode valores) {
		int largo = Math.min(DIMENSION, valores.size());
		float[] vector = new float[DIMENSION];
		double suma = 0;
		for (int i = 0; i < largo; i++) {
			vector[i] = (float) valores.get(i).asDouble();
			suma += vector[i] * vector[i];
		}
		double norma = Math.sqrt(suma);
		if (norma > 0) {
			for (int i = 0; i < DIMENSION; i++) {
				vector[i] = (float) (vector[i] / norma);
			}
		}
		return vector;
	}
}
