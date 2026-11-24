package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FuenteLectura;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.LecturaKm;
import java.time.Instant;

/** Lectura de kilometraje que el cliente registró (V2-M05.03). */
public record LecturaKmApp(Long id, int km, FuenteLectura fuente, Instant registradaEn) {

	static LecturaKmApp de(LecturaKm lectura) {
		return new LecturaKmApp(lectura.getId(), lectura.getKm(), lectura.getFuente(), lectura.getRegistradaEn());
	}
}
