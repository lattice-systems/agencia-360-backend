package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DireccionCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaOcupada;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.DayOfWeek;
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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda de citas de servicio de la app (APP-3.3). Las franjas salen de la
 * tabla {@code franja_horario} (V2-T05.07); el cupo de cada una es su capacidad
 * menos sus citas confirmadas en esa fecha. Dos citas a la vez no pasan del
 * cupo: agendar bloquea la franja hasta terminar la transacción.
 */
@Service
public class AgendaAppService {

	/** Las fechas y horas de la agenda son hora local de la agencia. */
	static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	/** Lo más que abarca una consulta de disponibilidad. */
	static final int DIAS_MAXIMOS_POR_CONSULTA = 31;

	private static final LocalDate SIN_INICIO = LocalDate.of(1900, 1, 1);
	/** Id que ninguna cita tiene: para no excluir ninguna al revisar duplicadas. */
	private static final Long SIN_CITA = -1L;
	private static final LocalDate SIN_FIN = LocalDate.of(9999, 12, 31);
	private static final long FRANJAS_POR_DIA_EN_BLOQUEO = 1_000_000L;

	private final HorarioTaller horario;
	private final CitaRepository citaRepository;
	private final VehiculosDeClientes vehiculos;
	private final Clock reloj;
	private final TicketCitaPdf ticketCitaPdf = new TicketCitaPdf();
	private final FirmaCitas firmaCitas;
	private final RecoleccionService recoleccion;

	@Autowired
	public AgendaAppService(HorarioTaller horario, CitaRepository citaRepository, VehiculosDeClientes vehiculos,
			FirmaCitas firmaCitas, RecoleccionService recoleccion) {
		this(horario, citaRepository, vehiculos, firmaCitas, recoleccion, Clock.system(ZONA_AGENCIA));
	}

	AgendaAppService(HorarioTaller horario, CitaRepository citaRepository, VehiculosDeClientes vehiculos,
			FirmaCitas firmaCitas, RecoleccionService recoleccion, Clock reloj) {
		this.recoleccion = recoleccion;
		this.firmaCitas = firmaCitas;
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
		Map<DayOfWeek, List<FranjaHorario>> franjasPorDia = horario.franjasPorDia();
		List<DisponibilidadApp> disponibles = new ArrayList<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta) && !fecha.isAfter(limite); fecha = fecha.plusDays(1)) {
			for (FranjaHorario franja : franjasPorDia.getOrDefault(fecha.getDayOfWeek(), List.of())) {
				if (!fecha.atTime(franja.getHoraInicio()).isAfter(ahora)) {
					continue;
				}
				long cupo = franja.getCapacidad() - ocupadas.getOrDefault(clave(fecha, franja.getId()), 0L);
				if (cupo > 0) {
					disponibles.add(new DisponibilidadApp(fecha, franja.getId(), franja.getHoraInicio(),
							franja.getHoraFin(), (int) cupo));
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
		Page<Cita> pedidas = citaRepository.delCliente(clienteId, estados, desde == null ? SIN_INICIO : desde,
				hasta == null ? SIN_FIN : hasta, Pagina.pedir(pagina, tamano));
		Map<Long, RecoleccionApp> recolecciones = recoleccion
				.deCitas(pedidas.getContent().stream().map(Cita::getId).toList());
		return Pagina.de(pedidas, cita -> CitaApp.de(cita, descripciones.get(cita.getVehiculoClienteId()))
				.conRecoleccion(recolecciones.get(cita.getId())));
	}

	/**
	 * Agenda una cita confirmada en la franja.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta un dato, la franja no existe, no es de ese día, ya
	 *             empezó o pasa del límite de anticipación (400)
	 * @throws ConflictoException
	 *             con {@code FRANJA_SIN_CUPO} si la franja ya no tiene cupo, o con
	 *             {@code CITA_DUPLICADA} si el vehículo ya tiene cita ese día (409)
	 */
	public CitaApp agendar(Long clienteId, Long vehiculoId, Long franjaId, LocalDate fecha, MotivoCita motivo) {
		return agendar(clienteId, vehiculoId, franjaId, fecha, motivo, null);
	}

	/**
	 * Agenda una cita confirmada y, si viene {@code direccionId}, los traslados de
	 * recoger y entregar en la misma transacción: si algo falla no queda ni cita ni
	 * traslados (V2-M17.02).
	 *
	 * @param direccionId
	 *            dirección guardada del cliente para la recolección; nula si no la
	 *            pide
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo o la dirección no existen o no son del cliente
	 *             (404)
	 * @throws NoProcesableException
	 *             con {@code FUERA_DE_COBERTURA} si la dirección ya no está en la
	 *             zona (422)
	 */
	@Transactional
	public CitaApp agendar(Long clienteId, Long vehiculoId, Long franjaId, LocalDate fecha, MotivoCita motivo,
			Long direccionId) {
		if (vehiculoId == null || franjaId == null || fecha == null) {
			throw new IllegalArgumentException("El vehículo, la franja y la fecha son obligatorios");
		}
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.filter(propio -> propio.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		FranjaHorario franja = validarFranja(fecha, franjaId);
		DireccionCliente direccion = direccionId == null ? null : recoleccion.cubierta(clienteId, direccionId);
		citaRepository.bloquearFranja(claveDeBloqueo(fecha, franjaId));
		verificarDisponible(vehiculoId, fecha, franja, SIN_CITA);
		Cita nueva = Cita.confirmar(clienteId, vehiculoId, fecha, franjaId, franja.getHoraInicio(), franja.getHoraFin(),
				motivo);
		Cita guardada = citaRepository.save(nueva);
		CitaApp creada = CitaApp.de(guardada, CitaApp.describir(vehiculo));
		return direccion == null ? creada : creada.conRecoleccion(recoleccion.programar(direccion, guardada));
	}

	/**
	 * Mueve una cita confirmada que no ha empezado a otra franja: libera la
	 * anterior y ocupa la nueva en la misma transacción (V2-M02.13).
	 *
	 * @param fecha
	 *            nuevo día; si es {@code null} se queda el de la cita
	 * @throws RecursoNoEncontradoException
	 *             si la cita no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta la franja, la franja no existe, no es de ese día, ya
	 *             empezó, pasa del límite de anticipación o es la misma de la cita
	 *             (400)
	 * @throws ConflictoException
	 *             con {@code CITA_NO_REPROGRAMABLE}, {@code FRANJA_SIN_CUPO} o
	 *             {@code CITA_DUPLICADA} (409)
	 */
	@Transactional
	public CitaApp reprogramar(Long clienteId, Long citaId, Long franjaId, LocalDate fecha) {
		if (franjaId == null) {
			throw new IllegalArgumentException("La franja es obligatoria");
		}
		Cita cita = citaRepository.findByIdAndClienteId(citaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita " + citaId));
		LocalDate nuevoDia = fecha == null ? cita.getFecha() : fecha;
		if (nuevoDia.equals(cita.getFecha()) && franjaId.equals(cita.getFranjaId())) {
			throw new IllegalArgumentException("La cita ya está en esa franja");
		}
		FranjaHorario franja = validarFranja(nuevoDia, franjaId);
		LocalDateTime ahora = LocalDateTime.now(reloj);
		citaRepository.bloquearFranja(claveDeBloqueo(nuevoDia, franjaId));
		verificarDisponible(cita.getVehiculoClienteId(), nuevoDia, franja, cita.getId());
		cita.reprogramar(ahora, nuevoDia, franjaId, franja.getHoraInicio(), franja.getHoraFin());
		recoleccion.reprogramarDeCita(cita.getId(), nuevoDia);
		return CitaApp.de(cita, vehiculos.buscarPorId(cita.getVehiculoClienteId())
				.filter(vehiculo -> vehiculo.propietario().id().equals(clienteId)).map(CitaApp::describir).orElse(null))
				.conRecoleccion(recoleccion.deCita(cita.getId()));
	}

	private FranjaHorario validarFranja(LocalDate fecha, Long franjaId) {
		FranjaHorario franja = horario.franja(franjaId)
				.orElseThrow(() -> new IllegalArgumentException("No existe la franja " + franjaId));
		if (!franja.esDe(fecha)) {
			throw new IllegalArgumentException("La franja " + franjaId + " no es de ese día");
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		if (!fecha.atTime(franja.getHoraInicio()).isAfter(ahora)) {
			throw new IllegalArgumentException("La franja ya empezó o ya pasó");
		}
		if (fecha.isAfter(ahora.toLocalDate().plusDays(horario.diasDeAnticipacion()))) {
			throw new IllegalArgumentException(
					"Solo se puede agendar hasta " + horario.diasDeAnticipacion() + " días adelante");
		}
		return franja;
	}

	private void verificarDisponible(Long vehiculoId, LocalDate fecha, FranjaHorario franja, Long excepto) {
		if (citaRepository.existeCitaDelVehiculoEnElDia(vehiculoId, fecha, EstadoCita.CONFIRMADA, excepto)) {
			throw new ConflictoException("Ese vehículo ya tiene una cita ese día", CodigosError.CITA_DUPLICADA);
		}
		if (citaRepository.countByFechaAndFranjaIdAndEstado(fecha, franja.getId(), EstadoCita.CONFIRMADA) >= franja
				.getCapacidad()) {
			throw new ConflictoException("La franja ya no tiene cupo", CodigosError.FRANJA_SIN_CUPO);
		}
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
		recoleccion.cancelarDeCita(cita.getId());
		return CitaApp.de(cita, vehiculos.buscarPorId(cita.getVehiculoClienteId())
				.filter(vehiculo -> vehiculo.propietario().id().equals(clienteId)).map(CitaApp::describir).orElse(null))
				.conRecoleccion(recoleccion.deCita(cita.getId()));
	}

	private static String clave(LocalDate fecha, Long franjaId) {
		return fecha + "#" + franjaId;
	}

	private static long claveDeBloqueo(LocalDate fecha, Long franjaId) {
		return fecha.toEpochDay() * FRANJAS_POR_DIA_EN_BLOQUEO + franjaId;
	}

	/**
	 * Código del QR de la cita y sus datos, para que la app lo dibuje (V2-M02.11).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la cita no existe o no es del cliente (404)
	 * @throws ConflictoException
	 *             con {@code CITA_CANCELADA} si la cita está cancelada (409)
	 */
	@Transactional(readOnly = true)
	public ComprobanteCitaApp comprobante(Long clienteId, Long citaId) {
		Cita cita = citaRepository.findByIdAndClienteId(citaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita " + citaId));
		if (cita.getEstado() == EstadoCita.CANCELADA) {
			throw new ConflictoException("La cita está cancelada", CodigosError.CITA_CANCELADA);
		}
		String nombre = vehiculos.buscarPorId(cita.getVehiculoClienteId())
				.filter(propio -> propio.propietario().id().equals(clienteId)).map(CitaApp::describir).orElse(null);
		return new ComprobanteCitaApp(cita.getId(), firmaCitas.contenidoQr(cita.getId()), cita.getFecha(),
				cita.getHoraInicio(), cita.getHoraFin(), nombre);
	}

	/**
	 * PDF del comprobante de la cita, con el QR firmado para recepción (V2-M02.11).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la cita no existe o no es del cliente (404)
	 */
	@Transactional(readOnly = true)
	public byte[] ticket(Long clienteId, Long citaId) {
		Cita cita = citaRepository.findByIdAndClienteId(citaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita " + citaId));
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(cita.getVehiculoClienteId())
				.filter(propio -> propio.propietario().id().equals(clienteId)).orElse(null);
		return ticketCitaPdf.armar(cita, vehiculo, firmaCitas.contenidoQr(cita.getId()));
	}
}
