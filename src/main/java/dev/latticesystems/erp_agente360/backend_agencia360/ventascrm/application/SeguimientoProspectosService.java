package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.HistorialEtapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento de prospectos por etapa (CRM-1.1) con la regla de visibilidad de
 * CRM-0.1:
 * <ul>
 * <li>Ventas ve y mueve solo sus prospectos.</li>
 * <li>Gerente de marketing ve todos, pero no cambia etapas; solo los
 * reasigna.</li>
 * <li>Los demás roles no ven la lista.</li>
 * </ul>
 * Un prospecto ajeno se trata como inexistente (404) para no confirmar que
 * existe.
 *
 * <p>
 * Quién mueve cada etapa (decisión del PO del 12 de octubre): el vendedor las
 * iniciales, Pago y Finalizado; Contabilidad pasa de Pago a Facturación al
 * validar el pago, y Facturación pasa de Facturación a Entrega al emitir la
 * factura. Contabilidad y Facturación lo harán desde las pantallas del ERP.
 */
@Service
public class SeguimientoProspectosService {

	private final ProspectoRepository prospectoRepository;
	private final DirectorioUsuarios directorioUsuarios;
	private final Clock reloj;

	@Autowired
	public SeguimientoProspectosService(ProspectoRepository prospectoRepository,
			DirectorioUsuarios directorioUsuarios) {
		this(prospectoRepository, directorioUsuarios, Clock.systemUTC());
	}

	SeguimientoProspectosService(ProspectoRepository prospectoRepository, DirectorioUsuarios directorioUsuarios,
			Clock reloj) {
		this.prospectoRepository = prospectoRepository;
		this.directorioUsuarios = directorioUsuarios;
		this.reloj = reloj;
	}

	/**
	 * @param etapa
	 *            etapa por la que se filtra; {@code null} para todas
	 */
	@Transactional(readOnly = true)
	public List<ProspectoVista> listar(Etapa etapa, UsuarioAutenticado actor) {
		List<Prospecto> prospectos;
		if (veTodos(actor)) {
			prospectos = etapa == null
					? prospectoRepository.findAllByOrderByCreatedAtDescIdDesc()
					: prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(etapa);
		} else {
			prospectos = etapa == null
					? prospectoRepository.findByVendedorIdOrderByCreatedAtDescIdDesc(actor.id())
					: prospectoRepository.findByVendedorIdAndEtapaOrderByCreatedAtDescIdDesc(actor.id(), etapa);
		}
		return prospectos.stream().map(ProspectoVista::de).toList();
	}

	/**
	 * Historial de etapas, del más antiguo al más reciente, con quién hizo cada
	 * cambio.
	 */
	@Transactional(readOnly = true)
	public List<MovimientoVista> historial(Long prospectoId, UsuarioAutenticado actor) {
		List<HistorialEtapa> movimientos = buscarVisible(prospectoId, actor).getHistorial();
		Map<Long, String> nombres = directorioUsuarios
				.nombresDe(movimientos.stream().map(HistorialEtapa::getUsuarioId).toList());
		return movimientos.stream().map(m -> new MovimientoVista(m.getEtapa(), m.getOrigen(), m.getFechaHora(),
				m.getUsuarioId() == null ? null : nombres.get(m.getUsuarioId()))).toList();
	}

	/**
	 * Cambia la etapa según quién la pide: el vendedor dueño del prospecto,
	 * Contabilidad (de Pago a Facturación) o Facturación (de Facturación a
	 * Entrega).
	 *
	 * @param motivoCierre
	 *            obligatorio cuando el vendedor pasa a {@link Etapa#CERRADO}
	 * @throws AccessDeniedException
	 *             si el rol no puede hacer ese cambio (403)
	 * @throws RecursoNoEncontradoException
	 *             si el prospecto no existe o es de otro vendedor (404)
	 */
	@Transactional
	public ProspectoVista cambiarEtapa(Long prospectoId, Etapa etapa, String motivoCierre, UsuarioAutenticado actor) {
		Instant ahora = Instant.now(reloj);
		if (actor.tieneRol(Role.VENTAS)) {
			Prospecto prospecto = buscarDelVendedor(prospectoId, actor);
			prospecto.moverPorVendedor(etapa, motivoCierre, actor.id(), ahora);
			return ProspectoVista.de(prospecto);
		}
		if (actor.tieneRol(Role.CONTABILIDAD) && etapa == Etapa.FACTURACION) {
			Prospecto prospecto = buscarCualquiera(prospectoId);
			prospecto.validarPago(actor.id(), ahora);
			return ProspectoVista.de(prospecto);
		}
		if (actor.tieneRol(Role.FACTURACION) && etapa == Etapa.ENTREGA) {
			Prospecto prospecto = buscarCualquiera(prospectoId);
			prospecto.confirmarFacturacion(actor.id(), ahora);
			return ProspectoVista.de(prospecto);
		}
		throw new AccessDeniedException("Tu rol no puede pasar el prospecto a esa etapa");
	}

	private Prospecto buscarVisible(Long prospectoId, UsuarioAutenticado actor) {
		return veTodos(actor) ? buscarCualquiera(prospectoId) : buscarDelVendedor(prospectoId, actor);
	}

	private Prospecto buscarDelVendedor(Long prospectoId, UsuarioAutenticado actor) {
		return existente(prospectoRepository.findByIdAndVendedorId(prospectoId, actor.id()), prospectoId);
	}

	private Prospecto buscarCualquiera(Long prospectoId) {
		return existente(prospectoRepository.findById(prospectoId), prospectoId);
	}

	private static Prospecto existente(Optional<Prospecto> prospecto, Long prospectoId) {
		return prospecto.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
	}

	private static boolean veTodos(UsuarioAutenticado actor) {
		if (actor.tieneRol(Role.GERENTE_MARKETING)) {
			return true;
		}
		if (actor.tieneRol(Role.VENTAS)) {
			return false;
		}
		throw new AccessDeniedException("Tu rol no tiene acceso a los prospectos");
	}
}
