package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.LocalDate;

/**
 * Cuándo toca el siguiente servicio (V2-M05.02). {@code motivo} es {@code km} o
 * {@code tiempo}: lo que llegue primero.
 */
public record ProximoServicioApp(Long servicioId, String nombre, int kmObjetivo, LocalDate fechaEstimada, int faltanKm,
		int faltanDias, String motivo) {
}
