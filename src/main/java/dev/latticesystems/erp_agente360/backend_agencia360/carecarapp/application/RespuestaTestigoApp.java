package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/**
 * Lo que dice la foto de un testigo del tablero (V2-M24.05). Sin reconocer,
 * {@code testigo}, {@code gravedad} y {@code queHacer} son nulos.
 *
 * @param gravedad
 *            {@code roja}, {@code amarilla} o {@code informativa}
 */
public record RespuestaTestigoApp(long consultaId, boolean reconocido, String testigo, String gravedad, String queHacer,
		List<RespuestaAsistenteApp.FuenteAsistenteApp> fuentes) {
}
