package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProveedorIA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige el proveedor de IA (ADR 0007): {@code app.asistente.proveedor} vale
 * {@code gemini}, {@code falso} o {@code auto} (Gemini si hay llave; si no, el
 * falso). La llave nunca se registra en el log.
 */
@Configuration
public class ProveedorIAConfig {

	private static final Logger LOG = LoggerFactory.getLogger(ProveedorIAConfig.class);

	@Bean
	ProveedorIA proveedorIA(@Value("${app.asistente.proveedor:auto}") String proveedor,
			@Value("${app.asistente.gemini.llave:}") String llave,
			@Value("${app.asistente.modelo-texto:gemini-3.5-flash-lite}") String modeloTexto,
			@Value("${app.asistente.modelo-embedding:gemini-embedding-2}") String modeloEmbedding, ObjectMapper json) {
		boolean usarGemini = "gemini".equals(proveedor) || ("auto".equals(proveedor) && !llave.isBlank());
		if (usarGemini && llave.isBlank()) {
			throw new IllegalStateException("app.asistente.proveedor=gemini pide GEMINI_API_KEY");
		}
		if (usarGemini) {
			LOG.info("Asistente con Gemini ({} y {})", modeloTexto, modeloEmbedding);
			return new GeminiProveedorIA(llave, modeloTexto, modeloEmbedding, json);
		}
		LOG.info("Asistente con el proveedor falso (sin GEMINI_API_KEY)");
		return new ProveedorIAFalso();
	}
}
