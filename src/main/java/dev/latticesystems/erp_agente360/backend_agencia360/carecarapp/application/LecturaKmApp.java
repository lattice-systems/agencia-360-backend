package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FuenteLectura;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.LecturaKm;
import java.time.Instant;

/**
 * Lectura de kilometraje que el cliente registró (V2-M05.03). {@code fotoUrl}
 * es una URL firmada de 15 minutos y se omite si la lectura no tiene foto.
 */
public record LecturaKmApp(Long id, int km, FuenteLectura fuente, Instant registradaEn,
		@JsonInclude(JsonInclude.Include.NON_NULL) String fotoUrl) {

	static LecturaKmApp de(LecturaKm lectura, String fotoUrl) {
		return new LecturaKmApp(lectura.getId(), lectura.getKm(), lectura.getFuente(), lectura.getRegistradaEn(),
				fotoUrl);
	}
}
