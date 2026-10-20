package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.LiquidacionVista.PagoVista;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.EstadoLiquidacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Liquidacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.LiquidacionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación de los pagos de la venta (ERP-1.1, decisión del PO del Sprint
 * 2): Caja registra y envía los pagos de un prospecto en Pago; Contabilidad los
 * valida, y el prospecto pasa a Facturación, o los rechaza con motivo.
 */
@Service
public class LiquidacionesService {

	private final LiquidacionRepository liquidacionRepository;
	private final ProspectoRepository prospectoRepository;
	private final DirectorioUsuarios directorioUsuarios;
	private final Clock reloj;

	@Autowired
	public LiquidacionesService(LiquidacionRepository liquidacionRepository, ProspectoRepository prospectoRepository,
			DirectorioUsuarios directorioUsuarios) {
		this(liquidacionRepository, prospectoRepository, directorioUsuarios, Clock.systemUTC());
	}

	LiquidacionesService(LiquidacionRepository liquidacionRepository, ProspectoRepository prospectoRepository,
			DirectorioUsuarios directorioUsuarios, Clock reloj) {
		this.liquidacionRepository = liquidacionRepository;
		this.prospectoRepository = prospectoRepository;
		this.directorioUsuarios = directorioUsuarios;
		this.reloj = reloj;
	}

	/** Los prospectos en Pago, con sus pagos aunque aún no tengan ninguno. */
	@Transactional(readOnly = true)
	public List<LiquidacionVista> listar(UsuarioAutenticado actor) {
		exigirCajaOContabilidad(actor);
		List<Prospecto> enPago = prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(Etapa.PAGO);
		Map<Long, Liquidacion> porProspecto = liquidacionRepository
				.findByProspectoIdIn(enPago.stream().map(Prospecto::getId).toList()).stream()
				.collect(Collectors.toMap(l -> l.getProspecto().getId(), Function.identity()));
		Map<Long, String> nombres = nombresDe(enPago, porProspecto.values().stream().toList());
		return enPago.stream().map(p -> vista(p, porProspecto.get(p.getId()), nombres)).toList();
	}

	@Transactional(readOnly = true)
	public LiquidacionVista detalle(Long prospectoId, UsuarioAutenticado actor) {
		exigirCajaOContabilidad(actor);
		Prospecto prospecto = buscarProspecto(prospectoId);
		Liquidacion liquidacion = liquidacionRepository.findByProspectoId(prospectoId).orElse(null);
		return vista(prospecto, liquidacion);
	}

	@Transactional
	public LiquidacionVista registrarPago(Long prospectoId, BigDecimal monto, LocalDate fechaPago, String referencia,
			UsuarioAutenticado actor) {
		exigirRol(actor, Role.CAJA, "Solo Caja registra pagos");
		Prospecto prospecto = buscarProspecto(prospectoId);
		Liquidacion liquidacion = liquidacionRepository.findByProspectoId(prospectoId)
				.orElseGet(() -> Liquidacion.abrir(prospecto));
		liquidacion.registrarPago(monto, fechaPago, referencia, actor.id(),
				LocalDate.now(reloj.withZone(ZonaAgencia.ZONA)));
		return vista(prospecto, liquidacionRepository.saveAndFlush(liquidacion));
	}

	@Transactional
	public LiquidacionVista anularPago(Long prospectoId, Long pagoId, UsuarioAutenticado actor) {
		exigirRol(actor, Role.CAJA, "Solo Caja quita pagos");
		Liquidacion liquidacion = buscarLiquidacion(prospectoId);
		liquidacion.anularPago(pagoId, actor.id(), Instant.now(reloj));
		return vista(liquidacion.getProspecto(), liquidacionRepository.saveAndFlush(liquidacion));
	}

	@Transactional
	public LiquidacionVista enviar(Long prospectoId, UsuarioAutenticado actor) {
		exigirRol(actor, Role.CAJA, "Solo Caja envía los pagos a Contabilidad");
		Liquidacion liquidacion = buscarLiquidacion(prospectoId);
		liquidacion.enviar(actor.id(), Instant.now(reloj));
		return vista(liquidacion.getProspecto(), liquidacion);
	}

	@Transactional
	public LiquidacionVista validar(Long prospectoId, UsuarioAutenticado actor) {
		exigirRol(actor, Role.CONTABILIDAD, "Solo Contabilidad valida los pagos");
		Liquidacion liquidacion = buscarLiquidacion(prospectoId);
		liquidacion.validar(actor.id(), Instant.now(reloj));
		return vista(liquidacion.getProspecto(), liquidacion);
	}

	@Transactional
	public LiquidacionVista rechazar(Long prospectoId, String motivo, UsuarioAutenticado actor) {
		exigirRol(actor, Role.CONTABILIDAD, "Solo Contabilidad rechaza los pagos");
		Liquidacion liquidacion = buscarLiquidacion(prospectoId);
		liquidacion.rechazar(motivo, actor.id(), Instant.now(reloj));
		return vista(liquidacion.getProspecto(), liquidacion);
	}

	private Prospecto buscarProspecto(Long prospectoId) {
		return prospectoRepository.findById(prospectoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
	}

	private Liquidacion buscarLiquidacion(Long prospectoId) {
		buscarProspecto(prospectoId);
		return liquidacionRepository.findByProspectoId(prospectoId).orElseThrow(
				() -> new RecursoNoEncontradoException("El prospecto " + prospectoId + " aún no tiene pagos"));
	}

	private LiquidacionVista vista(Prospecto prospecto, Liquidacion liquidacion) {
		List<Liquidacion> lista = liquidacion == null ? List.of() : List.of(liquidacion);
		return vista(prospecto, liquidacion, nombresDe(List.of(prospecto), lista));
	}

	private static LiquidacionVista vista(Prospecto prospecto, Liquidacion liquidacion, Map<Long, String> nombres) {
		String vendedor = nombres.get(prospecto.getVendedorId());
		if (liquidacion == null) {
			return new LiquidacionVista(prospecto.getId(), prospecto.getNombre(), vendedor, EstadoLiquidacion.ABIERTA,
					BigDecimal.ZERO.setScale(2), List.of(), null, null);
		}
		List<PagoVista> pagos = liquidacion.getPagos().stream()
				.map(p -> new PagoVista(p.getId(), p.getMonto(), p.getFechaPago(), p.getReferencia(),
						nombres.get(p.getRegistradoPorId()), p.getCreatedAt(), p.isAnulado(),
						p.getAnuladoPorId() == null ? null : nombres.get(p.getAnuladoPorId())))
				.toList();
		return new LiquidacionVista(prospecto.getId(), prospecto.getNombre(), vendedor, liquidacion.getEstado(),
				liquidacion.total().setScale(2), pagos, liquidacion.getEnviadaEn(), liquidacion.getMotivoRechazo());
	}

	private Map<Long, String> nombresDe(List<Prospecto> prospectos, List<Liquidacion> liquidaciones) {
		List<Long> ids = new ArrayList<>(prospectos.stream().map(Prospecto::getVendedorId).toList());
		liquidaciones.stream().flatMap(l -> l.getPagos().stream()).forEach(p -> {
			ids.add(p.getRegistradoPorId());
			if (p.getAnuladoPorId() != null) {
				ids.add(p.getAnuladoPorId());
			}
		});
		return directorioUsuarios.nombresDe(ids.stream().distinct().toList());
	}

	private static void exigirCajaOContabilidad(UsuarioAutenticado actor) {
		if (!actor.tieneRol(Role.CAJA) && !actor.tieneRol(Role.CONTABILIDAD)) {
			throw new AccessDeniedException("Solo Caja y Contabilidad ven los pagos");
		}
	}

	private static void exigirRol(UsuarioAutenticado actor, Role rol, String mensaje) {
		if (!actor.tieneRol(rol)) {
			throw new AccessDeniedException(mensaje);
		}
	}
}
