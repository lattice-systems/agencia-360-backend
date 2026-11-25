package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Gasolina;
import java.time.Instant;
import java.util.List;

/**
 * Checklist del traslado visto por el cliente (V2-M17.04). {@code fotos} y
 * {@code firmaUrl} son URL firmadas de 15 minutos; {@code notas} y
 * {@code firmaUrl} se omiten si no hay.
 */
public record ChecklistTrasladoApp(Long trasladoId, int km, Gasolina gasolina,
		@JsonInclude(JsonInclude.Include.NON_NULL) String notas, List<String> fotos,
		@JsonInclude(JsonInclude.Include.NON_NULL) String firmaUrl, Instant registradoEn) {
}
