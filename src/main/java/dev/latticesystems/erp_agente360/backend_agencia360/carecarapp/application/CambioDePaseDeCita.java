package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Una cita con pase de Wallet se canceló o se reprogramó (V2-M25.06). Se
 * publica dentro de la transacción que la cambia; quien mantiene los pases
 * (Google hoy, Apple cuando tenga servicio de actualización) lo escucha y los
 * actualiza después de confirmar.
 *
 * @param inicio
 *            el inicio vigente de la cita (el nuevo, si se reprogramó)
 * @param fin
 *            el fin vigente
 * @param vehiculoNombre
 *            nulo si no se conoce
 */
public record CambioDePaseDeCita(Long citaId, Tipo tipo, ZonedDateTime inicio, ZonedDateTime fin,
		String vehiculoNombre) {

	/** Qué le pasó a la cita. */
	public enum Tipo {
		CANCELADA, REPROGRAMADA
	}

	public CambioDePaseDeCita {
		Objects.requireNonNull(citaId, "citaId");
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(inicio, "inicio");
		Objects.requireNonNull(fin, "fin");
	}
}
