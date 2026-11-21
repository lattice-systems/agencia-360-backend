package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Horario del taller para la agenda de la app (APP-3.3), tomado de
 * {@code app.agenda.*}: los días que abre, sus franjas del día y cuántas citas
 * admite cada una. Mientras no exista la agenda del ERP (ERP-3.3) todos los
 * días que abre tienen las mismas franjas.
 */
@Component
public class HorarioTaller {

	/** Una franja del día; {@code id} es su número dentro del día, desde 1. */
	public record Franja(long id, LocalTime inicio, LocalTime fin) {
	}

	private final Set<DayOfWeek> dias;
	private final List<Franja> franjas;
	private final int cupoPorFranja;
	private final Duration plazoParaCancelar;
	private final int diasDeAnticipacion;

	@Autowired
	public HorarioTaller(
			@Value("${app.agenda.dias:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY}") List<DayOfWeek> dias,
			@Value("${app.agenda.apertura:09:00}") LocalTime apertura,
			@Value("${app.agenda.cierre:17:00}") LocalTime cierre,
			@Value("${app.agenda.duracion-franja-minutos:60}") int minutosPorFranja,
			@Value("${app.agenda.cupo-por-franja:3}") int cupoPorFranja,
			@Value("${app.agenda.horas-para-cancelar:24}") int horasParaCancelar,
			@Value("${app.agenda.dias-de-anticipacion:60}") int diasDeAnticipacion) {
		if (dias.isEmpty() || minutosPorFranja <= 0 || cupoPorFranja <= 0 || horasParaCancelar < 0
				|| diasDeAnticipacion <= 0 || !cierre.isAfter(apertura)) {
			throw new IllegalStateException("La configuración app.agenda.* no es válida");
		}
		this.dias = EnumSet.copyOf(dias);
		this.cupoPorFranja = cupoPorFranja;
		this.plazoParaCancelar = Duration.ofHours(horasParaCancelar);
		this.diasDeAnticipacion = diasDeAnticipacion;
		List<Franja> delDia = new ArrayList<>();
		LocalTime inicio = apertura;
		Duration duracion = Duration.ofMinutes(minutosPorFranja);
		while (!inicio.plus(duracion).isAfter(cierre) && inicio.plus(duracion).isAfter(inicio)) {
			delDia.add(new Franja(delDia.size() + 1L, inicio, inicio.plus(duracion)));
			inicio = inicio.plus(duracion);
		}
		if (delDia.isEmpty()) {
			throw new IllegalStateException("El horario app.agenda.* no deja ninguna franja");
		}
		this.franjas = List.copyOf(delDia);
	}

	public boolean abre(LocalDate fecha) {
		return dias.contains(fecha.getDayOfWeek());
	}

	public List<Franja> franjas() {
		return franjas;
	}

	public Optional<Franja> franja(long id) {
		return franjas.stream().filter(franja -> franja.id() == id).findFirst();
	}

	public int cupoPorFranja() {
		return cupoPorFranja;
	}

	public Duration plazoParaCancelar() {
		return plazoParaCancelar;
	}

	/** Hasta cuántos días adelante se puede agendar. */
	public int diasDeAnticipacion() {
		return diasDeAnticipacion;
	}
}
