package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TomaValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Valuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ValuacionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeValuacionCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ValuacionSolicitada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Valuación formal (M26). El prospecto lo crea ventascrm por evento cuando el
 * cliente no tiene uno abierto.
 *
 * <p>
 * No hay job: al leer se pone al día (la cita cancelada regresa la valuación a
 * solicitada y la oferta cuya vigencia pasó queda vencida). Por eso hasta las
 * consultas son transacciones de escritura.
 */
@Service
public class ValuacionService {

	private static final int FOTO_BYTES_MAXIMOS = 8 * 1024 * 1024;
	private static final int FOTO_LADO_MAXIMO = 1600;
	private static final String CARPETA_FOTOS = "valuacion-fotos";
	static final String EVENTO_OFERTA = "valuacion.oferta";

	/** Solicitudes de estreno donde se busca un prospecto que reutilizar. */
	private static final int ESTRENOS_A_REVISAR = 50;

	/**
	 * Plazos configurables, propuestas pendientes del PO.
	 *
	 * @param diasSinOferta
	 *            días tras una cita sin oferta para poder agendar otra
	 * @param vigenciaDias
	 *            vigencia de la oferta si no se indica
	 * @param vigenciaMaximaDias
	 *            lo más lejos que puede vencer una oferta
	 */
	record Plazos(int diasSinOferta, int vigenciaDias, int vigenciaMaximaDias) {
	}

	/**
	 * @param creada
	 *            {@code false} si ya había una abierta para ese vehículo
	 */
	public record Solicitud(ValuacionApp valuacion, boolean creada) {
	}

	private final ValuacionRepository valuaciones;
	private final EstimacionTomaCuentaRepository estimaciones;
	private final CitaRepository citas;
	private final VehiculosDeClientes vehiculos;
	private final SolicitudesEstreno solicitudesEstreno;
	private final ConsultaProspectos prospectos;
	private final ConsultaVendedores vendedores;
	private final DirectorioClientes clientes;
	private final TomaCuentaService tomaCuenta;
	private final AgendaAppService agenda;
	private final AlmacenArchivos archivos;
	private final ApplicationEventPublisher eventos;
	private final Plazos plazos;
	private final Clock reloj;

	@Autowired
	public ValuacionService(ValuacionRepository valuaciones, EstimacionTomaCuentaRepository estimaciones,
			CitaRepository citas, VehiculosDeClientes vehiculos, SolicitudesEstreno solicitudesEstreno,
			ConsultaProspectos prospectos, ConsultaVendedores vendedores, DirectorioClientes clientes,
			TomaCuentaService tomaCuenta, AgendaAppService agenda, AlmacenArchivos archivos,
			ApplicationEventPublisher eventos, @Value("${app.valuacion.dias-sin-oferta:7}") int diasSinOferta,
			@Value("${app.valuacion.vigencia-dias:7}") int vigenciaDias,
			@Value("${app.valuacion.vigencia-maxima-dias:30}") int vigenciaMaximaDias) {
		this(valuaciones, estimaciones, citas, vehiculos, solicitudesEstreno, prospectos, vendedores, clientes,
				tomaCuenta, agenda, archivos, eventos, new Plazos(diasSinOferta, vigenciaDias, vigenciaMaximaDias),
				Clock.system(AgendaAppService.ZONA_AGENCIA));
	}

	ValuacionService(ValuacionRepository valuaciones, EstimacionTomaCuentaRepository estimaciones,
			CitaRepository citas, VehiculosDeClientes vehiculos, SolicitudesEstreno solicitudesEstreno,
			ConsultaProspectos prospectos, ConsultaVendedores vendedores, DirectorioClientes clientes,
			TomaCuentaService tomaCuenta, AgendaAppService agenda, AlmacenArchivos archivos,
			ApplicationEventPublisher eventos, Plazos plazos, Clock reloj) {
		this.valuaciones = valuaciones;
		this.estimaciones = estimaciones;
		this.citas = citas;
		this.vehiculos = vehiculos;
		this.solicitudesEstreno = solicitudesEstreno;
		this.prospectos = prospectos;
		this.vendedores = vendedores;
		this.clientes = clientes;
		this.tomaCuenta = tomaCuenta;
		this.agenda = agenda;
		this.archivos = archivos;
		this.eventos = eventos;
		this.plazos = plazos;
		this.reloj = reloj;
	}

	/**
	 * Pide una valuación formal desde una estimación propia (V2-M26.01).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la estimación no existe o no es del cliente, o su vehículo ya
	 *             no es suyo (404)
	 * @throws NoProcesableException
	 *             con {@code VALUACION_SIN_VEHICULO} si se estimó sin un vehículo
	 *             registrado (422)
	 */
	@Transactional
	public Solicitud solicitar(Long clienteId, Long estimacionId) {
		EstimacionTomaCuenta estimacion = estimaciones.findById(estimacionId)
				.filter(encontrada -> encontrada.getClienteId().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la estimación " + estimacionId));
		Long vehiculoId = estimacion.getVehiculoClienteId();
		if (vehiculoId == null) {
			throw new NoProcesableException(
					"Esa estimación se hizo sin un vehículo registrado; pide la valuación en la agencia",
					CodigosError.VALUACION_SIN_VEHICULO);
		}
		if (vehiculos.buscarPorId(vehiculoId).filter(propio -> propio.propietario().id().equals(clienteId)).isEmpty()) {
			throw new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId);
		}
		valuaciones.bloquearVehiculo(vehiculoId);
		ponerAlDia(clienteId);
		Optional<Valuacion> abierta = valuaciones.findFirstByClienteIdAndVehiculoClienteIdAndEstadoIn(clienteId,
				vehiculoId, EstadoValuacion.abiertos());
		if (abierta.isPresent()) {
			return new Solicitud(comoApp(abierta.get()), false);
		}
		Valuacion nueva = Valuacion.solicitar(clienteId, estimacionId, vehiculoId);
		Optional<Long> prospecto = prospectoAbierto(clienteId, estimacionId);
		prospecto.ifPresent(nueva::ligarProspecto);
		Valuacion guardada = valuaciones.save(nueva);
		if (prospecto.isEmpty()) {
			DirectorioClientes.ClienteRegistrado cliente = clientes.buscarPorId(clienteId)
					.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
			eventos.publishEvent(new ValuacionSolicitada(guardada.getId(), cliente.nombre(), cliente.correo(), null));
		}
		return new Solicitud(comoApp(guardada), true);
	}

	/** Las del cliente, la más reciente primero (V2-M26.01). */
	@Transactional
	public Pagina<ValuacionApp> delCliente(Long clienteId, Integer pagina, Integer tamano) {
		Pageable pedida = Pagina.pedir(pagina, tamano);
		ponerAlDia(clienteId);
		return Pagina.de(valuaciones.findByClienteIdOrderByCreatedAtDescIdDesc(clienteId, pedida), this::comoApp);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 */
	@Transactional
	public ValuacionApp ver(Long clienteId, Long valuacionId) {
		return comoApp(propia(clienteId, valuacionId));
	}

	/**
	 * Guarda o reemplaza la foto de una toma, sin EXIF ni ubicación (V2-M26.02).
	 * La reemplazada se borra del almacén cuando la transacción se confirma, y la
	 * nueva si se revierte.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la valuación no es del cliente (404)
	 * @throws ConflictoException
	 *             con {@code VALUACION_CERRADA} (409)
	 * @throws IllegalArgumentException
	 *             si falta la foto, pasa de 8 MB o 40 megapíxeles o no es un JPEG
	 *             o PNG legible (400)
	 */
	@Transactional
	public ValuacionApp subirFoto(Long clienteId, Long valuacionId, TomaValuacion toma, byte[] imagen) {
		Valuacion valuacion = propia(clienteId, valuacionId);
		valuacion.exigirQueAdmitaFotos();
		if (imagen == null || imagen.length == 0) {
			throw new IllegalArgumentException("Falta la foto");
		}
		if (imagen.length > FOTO_BYTES_MAXIMOS) {
			throw new IllegalArgumentException("La foto pasa de 8 MB");
		}
		byte[] limpia = LimpiadorImagen.limpiar(imagen, FOTO_LADO_MAXIMO);
		String clave = archivos.guardar(CARPETA_FOTOS, "image/jpeg", limpia).clave();
		Optional<String> anterior = valuacion.ponerFoto(toma, clave, reloj.instant());
		alTerminarLaTransaccion(anterior.orElse(null), clave);
		return comoApp(valuacion);
	}

	private void alTerminarLaTransaccion(String siSeConfirma, String siSeRevierte) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				if (siSeConfirma != null) {
					archivos.borrar(siSeConfirma);
				}
			}

			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					archivos.borrar(siSeRevierte);
				}
			}
		});
	}

	/**
	 * Agenda la inspección física con la agenda de citas, en la misma transacción
	 * (V2-M26.03).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la valuación o su vehículo no son del cliente (404)
	 * @throws ConflictoException
	 *             con {@code VALUACION_NO_AGENDABLE}, o los de la agenda (409)
	 * @throws IllegalArgumentException
	 *             si la franja o la fecha no son válidas (400)
	 */
	@Transactional
	public CitaApp agendar(Long clienteId, Long valuacionId, Long franjaId, LocalDate fecha) {
		Valuacion valuacion = propia(clienteId, valuacionId);
		valuacion.exigirAgendable(citaDe(valuacion).map(Cita::getFecha).orElse(null), hoy(), plazos.diasSinOferta());
		CitaApp cita = agenda.agendar(clienteId, valuacion.getVehiculoClienteId(), franjaId, fecha,
				MotivoCita.VALUACION);
		valuacion.inspeccionAgendada(cita.id());
		return cita;
	}

	/**
	 * El cliente acepta o rechaza la oferta que vio (V2-M26.04). No mueve la etapa
	 * del prospecto: eso lo hace el vendedor en el CRM.
	 *
	 * @throws ConflictoException
	 *             con {@code OFERTA_VENCIDA} u {@code OFERTA_NO_DISPONIBLE} (409)
	 */
	@Transactional
	public ValuacionApp responder(Long clienteId, Long valuacionId, boolean aceptada, BigDecimal monto) {
		// Sin ponerla al día antes: responder decide con la fecha, y así dice
		// OFERTA_VENCIDA en lugar de depender de un cambio que el error revierte.
		Valuacion valuacion = valuaciones.bloquearDeCliente(valuacionId, clienteId)
				.orElseThrow(() -> noExiste(valuacionId));
		valuacion.responder(aceptada, monto, hoy(), reloj.instant());
		return comoApp(valuacion);
	}

	/**
	 * Las valuaciones para el personal, las más antiguas primero. Ventas ve solo
	 * las de los prospectos que atiende.
	 *
	 * @param estado
	 *            filtro opcional
	 */
	@Transactional
	public Pagina<ValuacionPersonalApp> delPersonal(UsuarioAutenticado actor, EstadoValuacion estado, Integer pagina,
			Integer tamano) {
		Pageable pedida = Pagina.pedir(pagina, tamano);
		valuaciones.idsAtrasadas(hoy()).forEach(id -> valuaciones.bloquear(id).ifPresent(this::sincronizar));
		valuaciones.flush();
		Set<EstadoValuacion> estados = estado == null ? EnumSet.allOf(EstadoValuacion.class) : EnumSet.of(estado);
		return Pagina.de(actor.tieneRol(Role.ADMINISTRADOR)
				? valuaciones.porAtender(estados, pedida)
				: valuaciones.porAtenderDeProspectos(estados, prospectos.delVendedor(actor.id()), pedida),
				this::comoPersonal);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe o ventas no atiende su prospecto (404)
	 */
	@Transactional
	public ValuacionPersonalApp verDelPersonal(UsuarioAutenticado actor, Long valuacionId) {
		return comoPersonal(delActor(actor, valuacionId));
	}

	/**
	 * Captura la oferta tras la inspección, o renueva una vencida, y avisa al
	 * cliente sin el monto (V2-M26.04).
	 *
	 * @param vigenciaHasta
	 *            último día para responder; nula usa la vigencia por omisión
	 * @throws IllegalArgumentException
	 *             si el monto, la vigencia o la nota no son válidos (400)
	 * @throws RecursoNoEncontradoException
	 *             si no existe o ventas no atiende su prospecto (404)
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} (409)
	 */
	@Transactional
	public ValuacionPersonalApp ofertar(UsuarioAutenticado actor, Long valuacionId, BigDecimal monto,
			LocalDate vigenciaHasta, String nota) {
		Valuacion valuacion = delActor(actor, valuacionId);
		LocalDate hoy = hoy();
		LocalDate vigencia = vigenciaHasta == null ? hoy.plusDays(plazos.vigenciaDias()) : vigenciaHasta;
		Valuacion.validarOferta(monto, vigencia, nota, hoy, plazos.vigenciaMaximaDias());
		if (valuacion.getEstado() == EstadoValuacion.VENCIDA) {
			exigirQueNoHayaOtraAbierta(valuacion);
		}
		boolean inspeccionHecha = citaDe(valuacion)
				.map(cita -> cita.getEstado() == EstadoCita.CONFIRMADA && !cita.getFecha().isAfter(hoy)).orElse(false);
		valuacion.ofertar(monto, vigencia, nota, actor.id(), reloj.instant(), inspeccionHecha);
		eventos.publishEvent(new AvisoAlCliente(valuacion.getClienteId(), EVENTO_OFERTA,
				Map.of("valuacionId", valuacion.getId(), "vehiculoId", valuacion.getVehiculoClienteId(),
						"vigenciaHasta", vigencia.toString())));
		return comoPersonal(valuacion);
	}

	private void exigirQueNoHayaOtraAbierta(Valuacion valuacion) {
		valuaciones.bloquearVehiculo(valuacion.getVehiculoClienteId());
		ponerAlDia(valuacion.getClienteId());
		if (valuaciones.findFirstByClienteIdAndVehiculoClienteIdAndEstadoIn(valuacion.getClienteId(),
				valuacion.getVehiculoClienteId(), EstadoValuacion.abiertos()).isPresent()) {
			throw new ConflictoException("El cliente ya tiene otra valuación abierta de ese vehículo",
					CodigosError.TRANSICION_INVALIDA);
		}
	}

	/** La valuación bloqueada y al día, si el usuario del personal puede verla. */
	private Valuacion delActor(UsuarioAutenticado actor, Long valuacionId) {
		Valuacion valuacion = valuaciones.bloquear(valuacionId)
				.filter(encontrada -> actor.tieneRol(Role.ADMINISTRADOR)
						|| prospectos.delVendedor(actor.id()).contains(encontrada.getProspectoId()))
				.orElseThrow(() -> noExiste(valuacionId));
		sincronizar(valuacion);
		return valuacion;
	}

	private ValuacionPersonalApp comoPersonal(Valuacion valuacion) {
		ValuacionApp vista = comoApp(valuacion);
		String cliente = clientes.buscarPorId(valuacion.getClienteId())
				.map(DirectorioClientes.ClienteRegistrado::nombre).orElse(null);
		return new ValuacionPersonalApp(vista.id(), vista.estado(), vista.creadaEn(), valuacion.getClienteId(), cliente,
				vista.vehiculoId(), valuacion.getProspectoId(), vista.estimacion(), vista.fotos(), vista.cita(),
				vista.oferta(), vista.respondidaEn());
	}

	@ApplicationModuleListener
	void alCrearseProspecto(ProspectoDeValuacionCreado creado) {
		valuaciones.bloquear(creado.valuacionId())
				.ifPresent(valuacion -> valuacion.ligarProspecto(creado.prospectoId()));
	}

	/** La valuación del cliente, bloqueada y al día. */
	private Valuacion propia(Long clienteId, Long valuacionId) {
		Valuacion valuacion = valuaciones.bloquearDeCliente(valuacionId, clienteId)
				.orElseThrow(() -> noExiste(valuacionId));
		sincronizar(valuacion);
		return valuacion;
	}

	private static RecursoNoEncontradoException noExiste(Long valuacionId) {
		return new RecursoNoEncontradoException("No existe la valuación " + valuacionId);
	}

	/**
	 * Pone al día las abiertas del cliente y guarda los cambios antes de crear o
	 * renovar, para que una que ya venció no choque con el índice único.
	 */
	private void ponerAlDia(Long clienteId) {
		for (Long id : valuaciones.idsDelCliente(clienteId, EstadoValuacion.abiertos())) {
			valuaciones.bloquear(id).ifPresent(this::sincronizar);
		}
		valuaciones.flush();
	}

	private void sincronizar(Valuacion valuacion) {
		if (valuacion.getEstado() == EstadoValuacion.CITA_AGENDADA && citaDe(valuacion)
				.map(cita -> cita.getEstado() == EstadoCita.CANCELADA).orElse(true)) {
			valuacion.reabrirSinCita();
		}
		valuacion.vencerSiPaso(hoy());
	}

	/**
	 * El primer prospecto sin terminar: el de la solicitud de estreno con la misma
	 * estimación, el de sus solicitudes de estreno más recientes y el de sus
	 * valuaciones.
	 */
	private Optional<Long> prospectoAbierto(Long clienteId, Long estimacionId) {
		List<SolicitudesEstreno.Solicitud> estrenos = solicitudesEstreno.delCliente(clienteId, 0, ESTRENOS_A_REVISAR);
		Stream<Long> mismaEstimacion = estrenos.stream()
				.filter(estreno -> estimacionId.equals(estreno.estimacionTomaCuentaId()))
				.map(SolicitudesEstreno.Solicitud::prospectoId);
		Stream<Long> deEstreno = estrenos.stream().map(SolicitudesEstreno.Solicitud::prospectoId);
		return Stream
				.of(mismaEstimacion, deEstreno,
						Stream.of(clienteId).flatMap(id -> valuaciones.prospectosDelCliente(id).stream()))
				.flatMap(candidatos -> candidatos).filter(Objects::nonNull)
				.filter(id -> prospectos.estado(id).map(estado -> !estado.terminado()).orElse(false)).findFirst();
	}

	private Optional<Cita> citaDe(Valuacion valuacion) {
		return valuacion.getCitaId() == null ? Optional.empty() : citas.findById(valuacion.getCitaId());
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj);
	}

	private ValuacionApp comoApp(Valuacion valuacion) {
		Optional<Cita> cita = citaDe(valuacion);
		return new ValuacionApp(valuacion.getId(), valuacion.getEstado(), valuacion.getCreatedAt(),
				valuacion.getVehiculoClienteId(), estimacionDe(valuacion), fotosDe(valuacion),
				valuacion.puedeAgendar(cita.map(Cita::getFecha).orElse(null), hoy(), plazos.diasSinOferta()),
				cita.map(c -> new ValuacionApp.Inspeccion(c.getId(), c.getFecha(), c.getHoraInicio(), c.getHoraFin()))
						.orElse(null),
				ofertaDe(valuacion), valuacion.getRespondidaEn(), asesorDe(valuacion));
	}

	private EstimacionTomaCuentaApp estimacionDe(Valuacion valuacion) {
		return estimaciones.findById(valuacion.getEstimacionTomaCuentaId()).map(tomaCuenta::comoApp).orElse(null);
	}

	private List<ValuacionApp.Foto> fotosDe(Valuacion valuacion) {
		return valuacion.getFotos().entrySet().stream().map(foto -> new ValuacionApp.Foto(foto.getKey(),
				archivos.urlTemporal(foto.getValue().clave()), foto.getValue().subidaEn())).toList();
	}

	private static ValuacionApp.Oferta ofertaDe(Valuacion valuacion) {
		return valuacion.getOferta() == null
				? null
				: new ValuacionApp.Oferta(valuacion.getOferta(), valuacion.getVigenciaHasta(),
						valuacion.getOfertaNota(), valuacion.getOfertadaEn());
	}

	private String asesorDe(Valuacion valuacion) {
		return valuacion.getProspectoId() == null
				? null
				: prospectos.estado(valuacion.getProspectoId())
						.flatMap(estado -> vendedores.nombreDe(estado.vendedorId())).orElse(null);
	}
}
