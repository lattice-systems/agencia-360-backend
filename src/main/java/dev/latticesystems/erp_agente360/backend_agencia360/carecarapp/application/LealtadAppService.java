package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Canje;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCanje;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.NivelLealtad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Recompensa;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CanjeRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.MovimientoPuntosRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RecompensaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Puntos, nivel, movimientos, recompensas y canjes del cliente (V2-M12.01,
 * V2-M12.02, V2-M12.03 y V2-M12.05).
 */
@Service
public class LealtadAppService {

	static final String REFERENCIA_ORDEN = "ORDEN";
	static final String REFERENCIA_CANJE = "CANJE";

	private static final Logger LOG = LoggerFactory.getLogger(LealtadAppService.class);
	private static final Duration DOCE_MESES = Duration.ofDays(365);
	private static final Set<MotivoPuntos> GANADOS = EnumSet.of(MotivoPuntos.SERVICIO, MotivoPuntos.REFERIDO,
			MotivoPuntos.AJUSTE);
	private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int LARGO_CODIGO = 8;

	private final MovimientoPuntosRepository movimientos;
	private final RecompensaRepository recompensas;
	private final CanjeRepository canjes;
	private final CuentaPuntosService cuenta;
	private final HistorialDeServicios historial;
	private final ReglasLealtad reglas;
	private final TransactionOperations transacciones;
	private final Clock reloj;
	private final SecureRandom azar = new SecureRandom();

	@Autowired
	public LealtadAppService(MovimientoPuntosRepository movimientos, RecompensaRepository recompensas,
			CanjeRepository canjes, CuentaPuntosService cuenta, HistorialDeServicios historial, ReglasLealtad reglas,
			PlatformTransactionManager transacciones) {
		this(movimientos, recompensas, canjes, cuenta, historial, reglas, new TransactionTemplate(transacciones),
				Clock.systemUTC());
	}

	LealtadAppService(MovimientoPuntosRepository movimientos, RecompensaRepository recompensas, CanjeRepository canjes,
			CuentaPuntosService cuenta, HistorialDeServicios historial, ReglasLealtad reglas,
			TransactionOperations transacciones, Clock reloj) {
		this.movimientos = movimientos;
		this.recompensas = recompensas;
		this.canjes = canjes;
		this.cuenta = cuenta;
		this.historial = historial;
		this.reglas = reglas;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Saldo, nivel y lo que falta para el siguiente (V2-M12.01). */
	@Transactional(readOnly = true)
	public ResumenLealtadApp resumen(Long clienteId) {
		long saldo = movimientos.saldo(clienteId);
		long ganados = movimientos.ganadosDesde(clienteId, GANADOS, reloj.instant().minus(DOCE_MESES));
		NivelLealtad nivel = reglas.nivel(ganados);
		NivelLealtad siguiente = reglas.siguiente(nivel);
		long faltan = siguiente == null ? 0 : reglas.umbral(siguiente) - ganados;
		return new ResumenLealtadApp(saldo, nivel, ganados, siguiente, faltan, ReferidosAppService.codigoDe(clienteId));
	}

	/** Del más reciente al más viejo (V2-M12.05). */
	@Transactional(readOnly = true)
	public Pagina<MovimientoPuntosApp> movimientos(Long clienteId, Integer pagina, Integer tamano) {
		return Pagina.de(movimientos.findByClienteIdOrderByCreadoEnDescIdDesc(clienteId, Pagina.pedir(pagina, tamano)),
				MovimientoPuntosApp::de);
	}

	/** Las activas, de la más barata a la más cara. */
	@Transactional(readOnly = true)
	public List<RecompensaApp> recompensas() {
		return recompensas.findByActivaTrueOrderByPuntosAscIdAsc().stream().map(RecompensaApp::de).toList();
	}

	/**
	 * Canjea una recompensa: descuenta sus puntos y crea un código vigente
	 * (V2-M12.03). El saldo del cliente y la fila de la recompensa quedan
	 * bloqueados, así que dos canjes a la vez nunca dejan el saldo negativo.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la recompensa no existe o no está activa (404)
	 * @throws ConflictoException
	 *             {@code SALDO_INSUFICIENTE} o {@code RECOMPENSA_AGOTADA} (409)
	 */
	@Transactional
	public CanjeApp canjear(Long clienteId, Long recompensaId) {
		cuenta.bloquear(clienteId);
		Recompensa recompensa = recompensas.bloquear(recompensaId).filter(Recompensa::isActiva)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la recompensa " + recompensaId));
		if (cuenta.saldo(clienteId) < recompensa.getPuntos()) {
			throw new ConflictoException("No te alcanzan los puntos para esta recompensa",
					CodigosError.SALDO_INSUFICIENTE);
		}
		if (recompensa.agotada()) {
			throw new ConflictoException("Esta recompensa se agotó", CodigosError.RECOMPENSA_AGOTADA);
		}
		recompensa.descontar();
		Instant ahora = reloj.instant();
		Instant vence = ahora.plus(reglas.vigenciaCanje());
		Canje canje = canjes.save(new Canje(clienteId, recompensa, codigoNuevo(), ahora, vence));
		cuenta.mover(clienteId, -recompensa.getPuntos(), MotivoPuntos.CANJE, REFERENCIA_CANJE, canje.getId());
		return CanjeApp.de(canje, recompensa.getNombre());
	}

	/** Del más nuevo al más viejo. */
	@Transactional(readOnly = true)
	public List<CanjeApp> canjes(Long clienteId) {
		List<Canje> propios = canjes.findByClienteIdOrderByCreadoEnDescIdDesc(clienteId);
		Map<Long, String> nombres = recompensas.findAllById(propios.stream().map(Canje::getRecompensaId).toList())
				.stream().collect(Collectors.toMap(Recompensa::getId, Recompensa::getNombre));
		return propios.stream().map(c -> CanjeApp.de(c, nombres.get(c.getRecompensaId()))).toList();
	}

	/**
	 * El personal usa el canje en la agencia (mientras el ERP siga congelado).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el código no existe (404)
	 * @throws ConflictoException
	 *             {@code CANJE_NO_VIGENTE} si ya se usó o venció (409)
	 */
	@Transactional
	public CanjeApp usar(String codigo) {
		Canje canje = canjes.findByCodigo(codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el canje " + codigo));
		if (!canje.usable(reloj.instant())) {
			throw new ConflictoException("El canje ya se usó o ya venció", CodigosError.CANJE_NO_VIGENTE);
		}
		canje.usar(reloj.instant());
		String nombre = recompensas.findById(canje.getRecompensaId()).map(Recompensa::getNombre).orElse(null);
		return CanjeApp.de(canje, nombre);
	}

	/**
	 * Puntos por un servicio entregado (V2-M12.02): un punto por cada
	 * {@code app.lealtad.pesos-por-punto} del total. Los servicios que se registran
	 * ya hechos no suman, y la misma orden suma una sola vez.
	 */
	@ApplicationModuleListener
	void alCambiarOrden(OrdenServicioCambiada cambio) {
		if (cambio.clienteId() == null || cambio.estatus() != EstatusOrden.ENTREGADO
				|| cambio.motivo() == MotivoCambio.REGISTRO) {
			return;
		}
		int puntos = historial.detalle(cambio.ordenId()).map(d -> reglas.puntosPorServicio(d.total())).orElse(0);
		if (puntos > 0) {
			cuenta.mover(cambio.clienteId(), puntos, MotivoPuntos.SERVICIO, REFERENCIA_ORDEN, cambio.ordenId());
		}
	}

	/**
	 * Todos los días a las 0:15 (hora de México) regresa los puntos de los canjes
	 * vencidos.
	 */
	@Scheduled(cron = "${app.lealtad.canje.vencer-cron:0 15 0 * * *}", zone = "America/Mexico_City")
	public void vencerTodos() {
		int vencidos = 0;
		for (Canje canje : canjes.findByEstadoAndVenceEnLessThanEqualOrderByIdAsc(EstadoCanje.VIGENTE,
				reloj.instant())) {
			try {
				transacciones.executeWithoutResult(estado -> vencer(canje.getId()));
				vencidos++;
			} catch (RuntimeException fallo) {
				LOG.warn("No se pudo vencer el canje {}", canje.getId(), fallo);
			}
		}
		LOG.info("Canjes vencidos: {}", vencidos);
	}

	/** Vence un canje y regresa sus puntos y su existencia. */
	@Transactional
	public void vencer(Long canjeId) {
		Canje canje = canjes.findById(canjeId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el canje " + canjeId));
		cuenta.bloquear(canje.getClienteId());
		canje.vencer(reloj.instant());
		recompensas.bloquear(canje.getRecompensaId()).ifPresent(Recompensa::devolver);
		cuenta.mover(canje.getClienteId(), canje.getPuntos(), MotivoPuntos.DEVOLUCION_CANJE, REFERENCIA_CANJE,
				canje.getId());
	}

	private String codigoNuevo() {
		String codigo;
		do {
			codigo = azar.ints(LARGO_CODIGO, 0, ALFABETO_CODIGO.length()).mapToObj(ALFABETO_CODIGO::charAt)
					.map(String::valueOf).collect(Collectors.joining());
		} while (canjes.existsByCodigo(codigo));
		return codigo;
	}
}
