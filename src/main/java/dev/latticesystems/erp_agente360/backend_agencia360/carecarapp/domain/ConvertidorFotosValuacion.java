package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * La columna {@code valuacion.fotos} de la ficha: un objeto JSON de toma a
 * {@code {"clave", "subidaEn"}}, guardado como texto.
 */
@Converter
public class ConvertidorFotosValuacion implements AttributeConverter<Map<TomaValuacion, FotoGuardada>, String> {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(Map<TomaValuacion, FotoGuardada> fotos) {
		ObjectNode raiz = JSON.createObjectNode();
		if (fotos != null) {
			fotos.forEach((toma, foto) -> raiz.putObject(toma.name()).put("clave", foto.clave()).put("subidaEn",
					foto.subidaEn().toString()));
		}
		return raiz.toString();
	}

	@Override
	public Map<TomaValuacion, FotoGuardada> convertToEntityAttribute(String texto) {
		Map<TomaValuacion, FotoGuardada> fotos = new EnumMap<>(TomaValuacion.class);
		try {
			JsonNode raiz = JSON.readTree(texto);
			raiz.fields().forEachRemaining(campo -> fotos.put(TomaValuacion.valueOf(campo.getKey()),
					new FotoGuardada(campo.getValue().get("clave").asText(),
							Instant.parse(campo.getValue().get("subidaEn").asText()))));
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Las fotos de la valuación no son JSON", ex);
		}
		return Collections.unmodifiableMap(fotos);
	}
}
