package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Un tramo de horario de un departamento de la sucursal (V2-M27.02). Los días
 * son ISO (1 lunes a 7 domingo) y las horas {@code HH:mm} en la hora de la
 * agencia. No cruza la medianoche. Vive dentro del {@code jsonb} de
 * {@code sucursal.horarios}.
 */
public record HorarioSucursal(String departamento, List<Integer> dias, String abre, String cierra) {

	private static final Set<String> DEPARTAMENTOS = Set.of("VENTAS", "SERVICIO", "REFACCIONES");

	/**
	 * @throws IllegalArgumentException
	 *             si el departamento, un día o una hora no son válidos, o si no
	 *             abre antes de cerrar
	 */
	public HorarioSucursal {
		if (departamento == null || !DEPARTAMENTOS.contains(departamento)) {
			throw new IllegalArgumentException("Departamento no válido: " + departamento);
		}
		if (dias == null || dias.isEmpty() || dias.stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
			throw new IllegalArgumentException("Los días van de 1 (lunes) a 7 (domingo)");
		}
		dias = List.copyOf(dias);
		if (hora(abre).compareTo(hora(cierra)) >= 0) {
			throw new IllegalArgumentException("El horario debe abrir antes de cerrar");
		}
	}

	/** Abierto en ese momento; la hora de cierre ya cuenta como cerrado. */
	public boolean abiertoEn(LocalDateTime momento) {
		if (!dias.contains(momento.getDayOfWeek().getValue())) {
			return false;
		}
		LocalTime hora = momento.toLocalTime();
		return !hora.isBefore(hora(abre)) && hora.isBefore(hora(cierra));
	}

	private static LocalTime hora(String texto) {
		try {
			return LocalTime.parse(texto);
		} catch (DateTimeParseException | NullPointerException e) {
			throw new IllegalArgumentException("Hora no válida (HH:mm): " + texto, e);
		}
	}
}
