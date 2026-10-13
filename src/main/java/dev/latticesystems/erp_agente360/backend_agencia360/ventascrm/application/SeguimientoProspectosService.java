package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.OrigenCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento de prospectos por etapa (CRM-1.1) con la regla de visibilidad de
 * CRM-0.1:
 * <ul>
 * <li>Ventas ve y cambia solo sus prospectos.</li>
 * <li>Gerente de marketing ve todos.</li>
 * <li>Los demás roles no ven prospectos; Contabilidad queda fuera hasta que el
 * PO defina su función en el CRM.</li>
 * </ul>
 * Un prospecto ajeno se trata como inexistente (404) para no confirmar que
 * existe.
 */
@Service
public class SeguimientoProspectosService {

	private final ProspectoRepository prospectoRepository;
	private final Clock reloj;

	@Autowired
	public SeguimientoProspectosService(ProspectoRepository prospectoRepository) {
		this(prospectoRepository, Clock.systemUTC());
	}

	SeguimientoProspectosService(ProspectoRepository prospectoRepository, Clock reloj) {
		this.prospectoRepository = prospectoRepository;
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

	@Transactional
	public ProspectoVista cambiarEtapa(Long prospectoId, Etapa etapa, UsuarioAutenticado actor) {
		Prospecto prospecto = buscarVisible(prospectoId, actor);
		prospecto.cambiarEtapa(etapa, OrigenCambio.CRM, Instant.now(reloj));
		return ProspectoVista.de(prospecto);
	}

	private Prospecto buscarVisible(Long prospectoId, UsuarioAutenticado actor) {
		Optional<Prospecto> prospecto = veTodos(actor)
				? prospectoRepository.findById(prospectoId)
				: prospectoRepository.findByIdAndVendedorId(prospectoId, actor.id());
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
