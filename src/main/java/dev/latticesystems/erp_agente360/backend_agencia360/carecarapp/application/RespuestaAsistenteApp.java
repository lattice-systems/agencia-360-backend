package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/**
 * Respuesta del asistente a una pregunta (V2-M24.03).
 *
 * @param encontrado
 *            false si el manual no tiene la respuesta; entonces no hay fuentes
 */
public record RespuestaAsistenteApp(long consultaId, String respuesta, boolean encontrado,
		List<FuenteAsistenteApp> fuentes) {

	/** Página del manual citada, con su texto para mostrar ("Manual, pág. 12"). */
	public record FuenteAsistenteApp(long manualId, int pagina, String titulo) {
	}
}
