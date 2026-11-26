package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.PiezasPorInstalar;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ResultadoInspeccion;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.EtapaOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.EvidenciaOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.OrdenServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.PuntoInspeccion;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TrabajoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.EtapaOrdenRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.EvidenciaOrdenRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.OrdenServicioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.PuntoInspeccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.TrabajoAdicionalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La orden en el taller (V2-M04.01, V2-M04.02, V2-M04.04 y V2-M04.05). Servicio
 * abre la orden, la pasa de etapa, registra la inspección con sus fotos y
 * videos y propone trabajos adicionales; el cliente sigue todo desde la app y
 * responde los adicionales con su firma. Cada cambio de la orden avisa con
 * {@link OrdenServicioCambiada} y su motivo; al quedar lista, los adicionales
 * aprobados pasan a ser renglones de la orden (V2-M04.07).
 */
@Service
public class TallerService implements TallerEnVivo {

	static final String CARPETA_EVIDENCIAS = "evidencias";
	static final String CARPETA_FIRMAS = "firmas";
	static final int FIRMA_MAXIMA = 1024 * 1024;
	private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
	private static final String SIN_ORDEN = "No existe la orden ";

	private final OrdenServicioRepository ordenes;
	private final EtapaOrdenRepository etapas;
	private final PuntoInspeccionRepository puntos;
	private final EvidenciaOrdenRepository evidencias;
	private final TrabajoAdicionalRepository adicionales;
	private final VehiculosDeClientes vehiculos;
	private final DirectorioUsuarios directorioUsuarios;
	private final AlmacenArchivos almacen;
	private final ApplicationEventPublisher eventos;
	private final PiezasPorInstalar piezas;
	private final Clock reloj;

	@Autowired
	public TallerService(OrdenServicioRepository ordenes, EtapaOrdenRepository etapas, PuntoInspeccionRepository puntos,
			EvidenciaOrdenRepository evidencias, TrabajoAdicionalRepository adicionales, VehiculosDeClientes vehiculos,
			DirectorioUsuarios directorioUsuarios, AlmacenArchivos almacen, ApplicationEventPublisher eventos,
			PiezasPorInstalar piezas) {
		this(ordenes, etapas, puntos, evidencias, adicionales, vehiculos, directorioUsuarios, almacen, eventos, piezas,
				Clock.system(ExpedienteService.ZONA_AGENCIA));
	}

	TallerService(OrdenServicioRepository ordenes, EtapaOrdenRepository etapas, PuntoInspeccionRepository puntos,
			EvidenciaOrdenRepository evidencias, TrabajoAdicionalRepository adicionales, VehiculosDeClientes vehiculos,
			DirectorioUsuarios directorioUsuarios, AlmacenArchivos almacen, ApplicationEventPublisher eventos,
			PiezasPorInstalar piezas, Clock reloj) {
		this.ordenes = ordenes;
		this.etapas = etapas;
		this.puntos = puntos;
		this.evidencias = evidencias;
		this.adicionales = adicionales;
		this.vehiculos = vehiculos;
		this.directorioUsuarios = directorioUsuarios;
		this.almacen = almacen;
		this.eventos = eventos;
		this.piezas = piezas;
		this.reloj = reloj;
	}

	/**
	 * Abre la orden cuando el vehículo entra al taller, con la etapa
	 * {@code RECIBIDO} en curso, a nombre de quien la recibe.
	 *
	 * @param entregaEstimada
	 *            hora de la agencia; {@code null} si aún no se estima
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe (404)
	 * @throws ConflictoException
	 *             si el vehículo ya tiene una orden sin entregar
	 *             ({@code ORDEN_ABIERTA}) (409)
	 * @throws IllegalArgumentException
	 *             si el kilometraje no es válido o la entrega ya pasó (400)
	 */
	@Transactional
	public OrdenEnVivo abrir(Long vehiculoId, int kilometraje, Long citaId, LocalDateTime entregaEstimada,
			UsuarioAutenticado actor) {
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		if (!ordenes.enCurso(List.of(vehiculoId), EstatusOrden.ENTREGADO).isEmpty()) {
			throw new ConflictoException("El vehículo ya tiene una orden sin entregar", CodigosError.ORDEN_ABIERTA);
		}
		ZonedDateTime ahora = ZonedDateTime.now(reloj);
		OrdenServicio orden = ordenes.save(
				OrdenServicio.abrir(vehiculoId, kilometraje, actor.id(), citaId, enLaAgencia(entregaEstimada), ahora));
		etapas.save(EtapaOrden.empezar(orden.getId(), EtapaTaller.RECIBIDO, ahora.toInstant(), null));
		avisar(orden, vehiculo, EtapaTaller.RECIBIDO, MotivoCambio.APERTURA);
		return vista(orden);
	}

	/**
	 * Termina la etapa en curso y empieza la que sigue. Se puede saltar una etapa
	 * (sin lavado, por ejemplo), pero nunca volver. Al quedar lista para entrega (o
	 * entregada sin pasar por lista), los trabajos adicionales aprobados pasan a la
	 * orden como renglones, para que el total y el ticket los traigan.
	 *
	 * @param entregaEstimada
	 *            nueva hora estimada de entrega en hora de la agencia; {@code null}
	 *            la deja como estaba
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe (404)
	 * @throws ConflictoException
	 *             si la orden ya se entregó o no se sigue en el taller
	 *             ({@code ORDEN_CERRADA}) (409)
	 * @throws IllegalArgumentException
	 *             si la etapa no va después de la actual, la nota es muy larga o la
	 *             entrega ya pasó (400)
	 */
	@Transactional
	public OrdenEnVivo avanzar(Long ordenId, EtapaTaller etapa, String nota, LocalDateTime entregaEstimada) {
		if (etapa == null) {
			throw new IllegalArgumentException("La etapa es obligatoria");
		}
		OrdenServicio orden = bloquear(ordenId);
		List<EtapaOrden> recorridas = etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(ordenId);
		if (recorridas.isEmpty() || orden.getEstatus() == EstatusOrden.ENTREGADO) {
			throw new ConflictoException("La orden ya se entregó o no se sigue en el taller",
					CodigosError.ORDEN_CERRADA);
		}
		EtapaOrden actual = recorridas.getLast();
		if (etapa.ordinal() <= actual.getEtapa().ordinal()) {
			throw new IllegalArgumentException("La orden ya pasó por la etapa " + etapa);
		}
		Instant ahora = Instant.now(reloj);
		EtapaOrden siguiente = EtapaOrden.empezar(ordenId, etapa, ahora, nota);
		if (entregaEstimada != null) {
			orden.estimarEntrega(enLaAgencia(entregaEstimada), ahora);
		}
		actual.terminar(ahora);
		etapas.save(siguiente);
		if (etapa.estatus().cerrada() && !orden.getEstatus().cerrada()) {
			incorporarAprobados(orden, ahora);
		}
		orden.pasarA(etapa, ahora);
		avisar(orden, etapa, MotivoCambio.ETAPA);
		return vista(orden);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe (404)
	 * @throws IllegalArgumentException
	 *             si el punto, el resultado o el comentario no son válidos (400)
	 */
	@Transactional
	public OrdenEnVivo revisar(Long ordenId, String punto, ResultadoInspeccion resultado, String comentario) {
		OrdenServicio orden = existente(ordenId);
		puntos.save(PuntoInspeccion.revisar(ordenId, punto, resultado, comentario));
		avisar(orden, MotivoCambio.INSPECCION);
		return vista(orden);
	}

	/**
	 * Guarda una foto o un video en el almacén de archivos, de la orden o de uno de
	 * sus puntos revisados.
	 *
	 * @param puntoId
	 *            punto revisado al que pertenece; {@code null} si es de la orden
	 * @throws RecursoNoEncontradoException
	 *             si la orden o el punto no existen (404)
	 * @throws IllegalArgumentException
	 *             si no es una foto JPEG, PNG o WebP ni un video MP4, o pasa de 20
	 *             MB (400)
	 */
	@Transactional
	public OrdenEnVivo agregarEvidencia(Long ordenId, Long puntoId, String tipoContenido, byte[] contenido) {
		OrdenServicio orden = existente(ordenId);
		if (puntoId != null && puntos.findByIdAndOrdenServicioId(puntoId, ordenId).isEmpty()) {
			throw new RecursoNoEncontradoException("La orden " + ordenId + " no tiene el punto " + puntoId);
		}
		var tipo = EvidenciaOrden.tipoDe(tipoContenido);
		String clave = almacen.guardar(CARPETA_EVIDENCIAS, tipoContenido, contenido).clave();
		evidencias.save(EvidenciaOrden.de(ordenId, puntoId, tipo, clave));
		avisar(orden, MotivoCambio.INSPECCION);
		return vista(orden);
	}

	/**
	 * Propone un trabajo adicional que el cliente aprueba o rechaza desde la app.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe (404)
	 * @throws ConflictoException
	 *             si la orden ya está terminada ({@code ORDEN_CERRADA}) (409)
	 * @throws IllegalArgumentException
	 *             si el concepto o los montos no son válidos (400)
	 */
	@Transactional
	public OrdenEnVivo proponer(Long ordenId, String concepto, BigDecimal manoDeObra, BigDecimal refacciones) {
		OrdenServicio orden = bloquear(ordenId);
		exigirAbierta(orden);
		adicionales.save(TrabajoAdicional.proponer(ordenId, concepto, manoDeObra, refacciones));
		avisar(orden, MotivoCambio.ADICIONAL);
		return vista(orden);
	}

	/**
	 * Agrega a la orden un renglón de mano de obra o de refacción, con el precio
	 * con IVA incluido (V2-M04.07). Es lo que el ticket de la orden cerrada trae.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe (404)
	 * @throws ConflictoException
	 *             si la orden ya está terminada ({@code ORDEN_CERRADA}) (409)
	 * @throws IllegalArgumentException
	 *             si el renglón no es válido o el total se pasa del máximo (400)
	 */
	@Transactional
	public OrdenEnVivo agregarRenglon(Long ordenId, RenglonNuevo renglon) {
		OrdenServicio orden = bloquear(ordenId);
		exigirAbierta(orden);
		orden.agregarRenglon(renglon);
		avisar(orden, MotivoCambio.RENGLON);
		return vista(orden);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe (404)
	 */
	@Transactional(readOnly = true)
	public OrdenEnVivo consultar(Long ordenId) {
		return vista(existente(ordenId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<OrdenEnCurso> enCurso(Collection<Long> vehiculoIds) {
		if (vehiculoIds == null || vehiculoIds.isEmpty()) {
			return List.of();
		}
		List<OrdenServicio> abiertas = ordenes.enCurso(vehiculoIds, EstatusOrden.ENTREGADO);
		Map<Long, String> nombres = directorioUsuarios
				.nombresDe(abiertas.stream().map(TallerService::asesorDe).distinct().toList());
		return abiertas.stream().map(orden -> {
			EtapaTaller etapa = etapaActual(orden, etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(orden.getId()));
			List<TrabajoAdicional> propuestos = adicionales.findByOrdenServicioIdOrderByIdAsc(orden.getId());
			int pendientes = (int) propuestos.stream().filter(TrabajoAdicional::estaPendiente).count();
			return new OrdenEnCurso(orden.getId(), orden.getVehiculoClienteId(), etapa, orden.getEstatus(),
					orden.getEntregaEstimada(), nombres.get(asesorDe(orden)), etapa.avance(), pendientes,
					orden.getTotal().add(sinIncorporar(propuestos)));
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<OrdenEnVivo> orden(Long ordenId) {
		return ordenes.findById(ordenId).map(this::vista);
	}

	@Override
	@Transactional
	public OrdenEnVivo responder(Long ordenId, Long adicionalId, boolean aprobado, byte[] firmaPng, Long clienteId) {
		OrdenServicio orden = bloquear(ordenId);
		TrabajoAdicional adicional = adicionales.findByIdAndOrdenServicioId(adicionalId, ordenId)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"La orden " + ordenId + " no tiene el trabajo adicional " + adicionalId));
		if (!adicional.estaPendiente()) {
			throw new ConflictoException("Ya respondiste este trabajo adicional", CodigosError.ADICIONAL_YA_RESPONDIDO);
		}
		exigirAbierta(orden);
		exigirFirmaPng(firmaPng);
		String clave = almacen.guardar(CARPETA_FIRMAS, "image/png", firmaPng).clave();
		adicional.responder(aprobado, clienteId, clave, Instant.now(reloj));
		avisar(orden, MotivoCambio.RESPUESTA);
		return vista(orden);
	}

	private OrdenServicio bloquear(Long ordenId) {
		return ordenes.bloquear(ordenId).orElseThrow(() -> new RecursoNoEncontradoException(SIN_ORDEN + ordenId));
	}

	private OrdenServicio existente(Long ordenId) {
		return ordenes.findById(ordenId).orElseThrow(() -> new RecursoNoEncontradoException(SIN_ORDEN + ordenId));
	}

	private static void exigirAbierta(OrdenServicio orden) {
		if (orden.getEstatus().cerrada()) {
			throw new ConflictoException("La orden ya está terminada; habla con tu asesor", CodigosError.ORDEN_CERRADA);
		}
	}

	private static void exigirFirmaPng(byte[] firma) {
		if (firma == null || firma.length <= FIRMA_PNG.length || firma.length > FIRMA_MAXIMA
				|| !Arrays.equals(Arrays.copyOf(firma, FIRMA_PNG.length), FIRMA_PNG)) {
			throw new IllegalArgumentException("La firma debe ser una imagen PNG de hasta 1 MB");
		}
	}

	private void incorporarAprobados(OrdenServicio orden, Instant ahora) {
		for (TrabajoAdicional trabajo : adicionales.findByOrdenServicioIdOrderByIdAsc(orden.getId())) {
			trabajo.incorporar(ahora).forEach(orden::agregarRenglon);
		}
	}

	private void avisar(OrdenServicio orden, MotivoCambio motivo) {
		avisar(orden, etapaActual(orden, etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(orden.getId())), motivo);
	}

	private void avisar(OrdenServicio orden, EtapaTaller etapa, MotivoCambio motivo) {
		vehiculos.buscarPorId(orden.getVehiculoClienteId())
				.ifPresent(vehiculo -> avisar(orden, vehiculo, etapa, motivo));
	}

	private void avisar(OrdenServicio orden, VehiculoDeCliente vehiculo, EtapaTaller etapa, MotivoCambio motivo) {
		eventos.publishEvent(new OrdenServicioCambiada(orden.getId(), orden.getVehiculoClienteId(),
				vehiculo.propietario().id(), orden.getEstatus(), etapa, motivo));
	}

	private OrdenEnVivo vista(OrdenServicio orden) {
		Long ordenId = orden.getId();
		List<EtapaOrden> recorridas = etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(ordenId);
		List<EvidenciaOrden> todas = evidencias.findByOrdenServicioIdOrderByIdAsc(ordenId);
		List<PuntoRevisado> inspeccion = puntos.findByOrdenServicioIdOrderByIdAsc(ordenId).stream()
				.map(punto -> revisado(punto, todas)).toList();
		List<TrabajoAdicional> propuestos = adicionales.findByOrdenServicioIdOrderByIdAsc(ordenId);
		BigDecimal aprobados = suma(propuestos, EstadoAdicional.APROBADO);
		BigDecimal pendientes = suma(propuestos, EstadoAdicional.PENDIENTE);
		EtapaTaller etapa = etapaActual(orden, recorridas);
		Long asesorId = asesorDe(orden);
		String asesor = directorioUsuarios.nombresDe(List.of(asesorId)).get(asesorId);
		return new OrdenEnVivo(ordenId, orden.getVehiculoClienteId(), orden.getEstatus(), etapa,
				orden.getEntregaEstimada(), asesor, etapa.avance(),
				recorridas.stream().map(TallerService::paso).toList(), inspeccion,
				evidenciasDe(todas, evidencia -> evidencia.getPuntoInspeccionId() == null),
				propuestos.stream().map(TallerService::adicional).toList(), orden.getTotal(), aprobados, pendientes,
				orden.getTotal().add(sinIncorporar(propuestos)), piezas.deCita(orden.getCitaId()));
	}

	private PuntoRevisado revisado(PuntoInspeccion punto, List<EvidenciaOrden> todas) {
		return new PuntoRevisado(punto.getId(), punto.getPunto(), punto.getResultado(), punto.getComentario(),
				evidenciasDe(todas, evidencia -> punto.getId().equals(evidencia.getPuntoInspeccionId())));
	}

	private static PasoDeEtapa paso(EtapaOrden etapa) {
		return new PasoDeEtapa(etapa.getEtapa(), etapa.getInicio(), etapa.getFin(), etapa.getNota());
	}

	private static Adicional adicional(TrabajoAdicional trabajo) {
		return new Adicional(trabajo.getId(), trabajo.getConcepto(), trabajo.getManoDeObra(), trabajo.getRefacciones(),
				trabajo.getTotal(), trabajo.getEstado(), trabajo.getRespondidoEn());
	}

	private List<Evidencia> evidenciasDe(List<EvidenciaOrden> todas, Predicate<EvidenciaOrden> filtro) {
		List<Evidencia> elegidas = new ArrayList<>();
		for (EvidenciaOrden evidencia : todas) {
			if (filtro.test(evidencia)) {
				String url = almacen.urlTemporal(evidencia.getUrl());
				elegidas.add(new Evidencia(evidencia.getId(), evidencia.getTipo(), url));
			}
		}
		return List.copyOf(elegidas);
	}

	private static BigDecimal sinIncorporar(List<TrabajoAdicional> trabajos) {
		return trabajos.stream()
				.filter(trabajo -> trabajo.getEstado() == EstadoAdicional.APROBADO && !trabajo.estaIncorporado())
				.map(TrabajoAdicional::getTotal).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
	}

	private static BigDecimal suma(List<TrabajoAdicional> trabajos, EstadoAdicional estado) {
		return trabajos.stream().filter(trabajo -> trabajo.getEstado() == estado).map(TrabajoAdicional::getTotal)
				.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
	}

	private static EtapaTaller etapaActual(OrdenServicio orden, List<EtapaOrden> recorridas) {
		if (!recorridas.isEmpty()) {
			return recorridas.getLast().getEtapa();
		}
		return switch (orden.getEstatus()) {
			case RECIBIDO -> EtapaTaller.RECIBIDO;
			case EN_PROCESO -> EtapaTaller.EN_REPARACION;
			case TERMINADO -> EtapaTaller.LISTO_PARA_ENTREGA;
			case ENTREGADO -> EtapaTaller.ENTREGADO;
		};
	}

	private static Long asesorDe(OrdenServicio orden) {
		return Objects.requireNonNullElse(orden.getAsesorId(), orden.getAtendidoPor());
	}

	private static Instant enLaAgencia(LocalDateTime momento) {
		return momento == null ? null : momento.atZone(ExpedienteService.ZONA_AGENCIA).toInstant();
	}
}
