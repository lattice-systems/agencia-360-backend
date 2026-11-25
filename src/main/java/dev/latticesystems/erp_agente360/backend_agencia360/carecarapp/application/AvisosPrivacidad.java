package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.Instant;
import java.util.Optional;

/** {@code aviso_privacidad} y {@code aceptacion_aviso} (V2-M22.01). */
public interface AvisosPrivacidad {

	/** Una versión del aviso, tal como se publicó. */
	record Aviso(long id, int version, String texto, Instant vigenteDesde) {
	}

	/** Que una cuenta aceptó una versión, y cuándo. */
	record Aceptacion(long avisoId, int version, Instant aceptadoEn) {
	}

	/** La versión mayor cuyo {@code vigente_desde} ya llegó. */
	Optional<Aviso> vigente(Instant ahora);

	Optional<Aviso> buscar(long id);

	/** La versión mayor publicada, esté en vigor o programada. */
	Optional<Aviso> ultima();

	/** Guarda una versión nueva (la mayor + 1). */
	Aviso publicar(String texto, Instant vigenteDesde);

	Optional<Aceptacion> aceptacionDe(long clienteId, long avisoId);

	/**
	 * Registra la aceptación de la cuenta del cliente. Si ya existía, devuelve la
	 * primera sin duplicarla.
	 */
	Aceptacion aceptar(long clienteId, long avisoId, Instant ahora);
}
