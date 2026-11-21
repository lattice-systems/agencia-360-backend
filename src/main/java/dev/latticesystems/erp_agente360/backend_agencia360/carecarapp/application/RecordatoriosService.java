package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush.Notificacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recordatorios de cita por push (APP-6): uno 24 horas antes y otro 2 horas
 * antes. Cada uno se manda una sola vez; si la cita se agendó con menos de 24
 * horas, solo va el de 2 horas.
 */
@Service
public class RecordatoriosService {

	static final Duration DIA_ANTES = Duration.ofHours(24);
	static final Duration HORAS_ANTES = Duration.ofHours(2);
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final CitaRepository citaRepository;
	private final NotificacionesService notificaciones;
	private final Clock reloj;

	@Autowired
	public RecordatoriosService(CitaRepository citaRepository, NotificacionesService notificaciones) {
		this(citaRepository, notificaciones, Clock.system(AgendaAppService.ZONA_AGENCIA));
	}

	RecordatoriosService(CitaRepository citaRepository, NotificacionesService notificaciones, Clock reloj) {
		this.citaRepository = citaRepository;
		this.notificaciones = notificaciones;
		this.reloj = reloj;
	}

	/** @return cuántos recordatorios tocaba mandar */
	@Transactional
	public int enviarPendientes() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		int enviados = 0;
		for (Cita cita : citaRepository.sinRecordatorio(EstadoCita.CONFIRMADA, ahora.toLocalDate(),
				ahora.plus(DIA_ANTES).toLocalDate())) {
			LocalDateTime inicio = cita.inicio();
			if (!inicio.isAfter(ahora)) {
				continue;
			}
			if (!inicio.isAfter(ahora.plus(HORAS_ANTES))) {
				cita.marcarRecordatorio2h(reloj.instant());
				avisar(cita, "Tu cita es en un rato",
						"Hoy a las " + HORA.format(cita.getHoraInicio()) + ". Te esperamos en el taller.");
				enviados++;
			} else if (cita.getRecordatorio24hEn() == null && !inicio.isAfter(ahora.plus(DIA_ANTES))) {
				cita.marcarRecordatorio24h(reloj.instant());
				avisar(cita, "Tu cita de servicio se acerca",
						(inicio.toLocalDate().equals(ahora.toLocalDate()) ? "Hoy" : "Mañana") + " a las "
								+ HORA.format(cita.getHoraInicio()) + ".");
				enviados++;
			}
		}
		return enviados;
	}

	private void avisar(Cita cita, String titulo, String cuerpo) {
		notificaciones.avisar(cita.getClienteId(),
				new Notificacion(titulo, cuerpo, Map.of("tipo", "CITA", "citaId", String.valueOf(cita.getId()))));
	}
}
