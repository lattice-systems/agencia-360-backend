package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaOcupada;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda de citas de servicio de la app (APP-3.3). Las franjas salen del
 * {@link HorarioTaller}; el cupo de cada una es la capacidad menos sus citas
 * confirmadas. Dos citas a la vez no pasan del cupo: agendar bloquea la franja
 * hasta terminar la transacción.
 */
@Service
public class AgendaAppService {

	/** Las fechas y horas de la agenda son hora local de la agencia. */
	static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	/** Lo más que abarca una consulta de disponibilidad. */
	static final int DIAS_MAXIMOS_POR_CONSULTA = 31;

	private static final LocalDate SIN_INICIO = LocalDate.of(1900, 1, 1);
	private static final LocalDate SIN_FIN = LocalDate.of(9999, 12, 31);

	private final HorarioTaller horario;
	private final CitaRepository citaRepository;
	private final VehiculosDeClientes vehiculos;
	private final Clock reloj;

	@Autowired
	public AgendaAppService(HorarioTaller horario, CitaRepository citaRepository, VehiculosDeClientes vehiculos) {
		this(horario, citaRepository, vehiculos, Clock.system(ZONA_AGENCIA));
	}

	AgendaAppService(HorarioTaller horario, CitaRepository citaRepository, VehiculosDeClientes vehiculos, Clock reloj) {
		this.horario = horario;
		this.citaRepository = citaRepository;
		this.vehiculos = vehiculos;
		this.reloj = reloj;
	}

	/**
	 * Las franjas con cupo entre las dos fechas, sin las que ya empezaron ni las
	 * que pasan del límite de anticipación.
	 *
	 * @throws IllegalArgumentException
	 *             si falta una fecha, {@code hasta} es anterior a {@code desde} o
	 *             el rango pasa de 31 días (400)
	 */
	@Transactional(readOnly = true)
	public List<DisponibilidadApp> disponibilidad(LocalDate desde, LocalDate hasta) {
		if (desde == null || hasta == null) {
			throw new IllegalArgumentException("Las fechas desde y hasta son obligatorias");
		}
		if (hasta.isBefore(desde)) {
			throw new IllegalArgumentException("La fecha hasta no puede ser anterior a desde");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) >= DIAS_MAXIMOS_POR_CONSULTA) {
			throw new IllegalArgumentException("Consulta hasta " + DIAS_MAXIMOS_POR_CONSULTA + " días a la vez");
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate limite = ahora.toLocalDate().plusDays(horario.diasDeAnticipacion());
		Map<String, Long> ocupadas = citaRepository.ocupadas(EstadoCita.CONFIRMADA, desde, hasta).stream()
				.collect(Collectors.toMap(franja -> clave(franja.fecha(), franja.franjaId()), FranjaOcupada::citas));
		List<DisponibilidadApp> disponibles = new ArrayList<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta) && !fecha.isAfter(limite); fecha = fecha.plusDays(1)) {
			if (!horario.abre(fecha)) {
				continue;
			}
			for (HorarioTaller.Franja franja : horario.franjas()) {
				if (!fecha.atTime(franja.inicio()).isAfter(ahora)) {
					continue;
				}
				long cupo = horario.cupoPorFranja() - ocupadas.getOrDefault(clave(fecha, franja.id()), 0L);
				if (cupo > 0) {
					int libres = (int) cupo;
					disponibles.add(new DisponibilidadApp(fecha, franja.id(), franja.inicio(), franja.fin(), libres));
				}
			}
		}
		return disponibles;
	}

	/**
	 * Las citas del cliente por fecha y hora de inicio. Los filtros son opcionales.
	 *
	 * @throws IllegalArgumentException
	 *             si la página no es válida (400)
	 */
	@Transactional(readOnly = true)
	public Pagina<CitaApp> citas(Long clienteId, EstadoCita estado, LocalDate desde, LocalDate hasta, Integer pagina,
			Integer tamano) {
		Set<EstadoCita> estados = estado == null ? EnumSet.allOf(EstadoCita.class) : EnumSet.of(estado);
		Map<Long, String> descripciones = vehiculos.deCliente(clienteId).stream()
				.collect(Collectors.toMap(VehiculoDeCliente::id, CitaApp::describir, (a, b) -> a));
		return Pagina.de(
				citaRepository.delCliente(clienteId, estados, desde == null ? SIN_INICIO : desde,
						hasta == null ? SIN_FIN : hasta, Pagina.pedir(pagina, tamano)),
				cita -> CitaApp.de(cita, descripciones.get(cita.getVehiculoClienteId())));
	}

	/**
	 * Agenda una cita confirmada en la franja.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta un dato, el taller no abre ese día, la franja no existe,
	 *             ya empezó o pasa del límite de anticipación (400)
	 * @throws ConflictoException
	 *             con {@code FRANJA_SIN_CUPO} si la franja ya no tiene cupo, o sin
	 *             código si el vehículo ya tiene cita en ella (409)
	 */
	@Transactional
	public CitaApp agendar(Long clienteId, Long vehiculoId, Long franjaId, LocalDate fecha) {
		if (vehiculoId == null || franjaId == null || fecha == null) {
			throw new IllegalArgumentException("El vehículo, la franja y la fecha son obligatorios");
		}
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.filter(propio -> propio.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		if (!horario.abre(fecha)) {
			throw new IllegalArgumentException("El taller no abre ese día");
		}
		HorarioTaller.Franja franja = horario.franja(franjaId)
				.orElseThrow(() -> new IllegalArgumentException("No existe la franja " + franjaId));
		LocalDateTime ahora = LocalDateTime.now(reloj);
		if (!fecha.atTime(franja.inicio()).isAfter(ahora)) {
			throw new IllegalArgumentException("La franja ya empezó o ya pasó");
		}
		if (fecha.isAfter(ahora.toLocalDate().plusDays(horario.diasDeAnticipacion()))) {
			throw new IllegalArgumentException(
					"Solo se puede agendar hasta " + horario.diasDeAnticipacion() + " días adelante");
		}
		citaRepository.bloquearFranja(fecha.toEpochDay() * 1_000 + franjaId);
		if (citaRepository.existsByVehiculoClienteIdAndFechaAndFranjaIdAndEstado(vehiculoId, fecha, franjaId,
				EstadoCita.CONFIRMADA)) {
			throw new ConflictoException("Ese vehículo ya tiene una cita en ese horario");
		}
		if (citaRepository.countByFechaAndFranjaIdAndEstado(fecha, franjaId, EstadoCita.CONFIRMADA) >= horario
				.cupoPorFranja()) {
			throw new ConflictoException("La franja ya no tiene cupo", CodigosError.FRANJA_SIN_CUPO);
		}
		return CitaApp.de(
				citaRepository
						.save(Cita.confirmar(clienteId, vehiculoId, fecha, franjaId, franja.inicio(), franja.fin())),
				CitaApp.describir(vehiculo));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la cita no existe o no es del cliente (404)
	 * @throws ConflictoException
	 *             con {@code CITA_NO_CANCELABLE} o {@code FUERA_DE_PLAZO} (409)
	 */
	@Transactional
	public CitaApp cancelar(Long clienteId, Long citaId) {
		Cita cita = citaRepository.findByIdAndClienteId(citaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita " + citaId));
		cita.cancelar(LocalDateTime.now(reloj), horario.plazoParaCancelar(), reloj.instant());
		return CitaApp.de(cita,
				vehiculos.buscarPorId(cita.getVehiculoClienteId())
						.filter(vehiculo -> vehiculo.propietario().id().equals(clienteId)).map(CitaApp::describir)
						.orElse(null));
	}

	private static String clave(LocalDate fecha, Long franjaId) {
		return fecha + "#" + franjaId;
	}
}
