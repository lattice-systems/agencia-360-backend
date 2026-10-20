package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reasignación de prospectos entre vendedores (CRM-0.1). Solo el Gerente de
 * marketing reasigna, y el nuevo responsable debe ser un usuario activo con rol
 * VENTAS.
 *
 * <p>
 * También reasigna varios a la vez (CRM-UX-1, acción masiva que confirmó el SM
 * en el Sprint 2): todos o ninguno.
 */
@Service
public class ReasignacionProspectosService {

	private static final int MAXIMO_POR_VEZ = 100;

	private final ProspectoRepository prospectoRepository;
	private final ConsultaVendedores consultaVendedores;

	public ReasignacionProspectosService(ProspectoRepository prospectoRepository,
			ConsultaVendedores consultaVendedores) {
		this.prospectoRepository = prospectoRepository;
		this.consultaVendedores = consultaVendedores;
	}

	/**
	 * @throws AccessDeniedException
	 *             si quien reasigna no es Gerente de marketing (403)
	 * @throws RecursoNoEncontradoException
	 *             si el prospecto no existe (404)
	 * @throws IllegalArgumentException
	 *             si el nuevo responsable no es un vendedor activo (400)
	 */
	@Transactional
	public ProspectoVista reasignar(Long prospectoId, Long vendedorId, UsuarioAutenticado actor) {
		exigirGerente(actor);
		Prospecto prospecto = prospectoRepository.findById(prospectoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
		exigirVendedorActivo(vendedorId);
		prospecto.reasignarA(vendedorId);
		return ProspectoVista.de(prospecto);
	}

	/**
	 * Pasa varios prospectos al mismo vendedor. Si alguno no existe, no se mueve
	 * ninguno.
	 *
	 * @return los prospectos en el orden en que se pidieron
	 * @throws AccessDeniedException
	 *             si quien reasigna no es Gerente de marketing (403)
	 * @throws IllegalArgumentException
	 *             si la lista está vacía, pasa de 100 o el responsable no es un
	 *             vendedor activo (400)
	 * @throws RecursoNoEncontradoException
	 *             si alguno de los prospectos no existe (404)
	 */
	@Transactional
	public List<ProspectoVista> reasignarVarios(List<Long> prospectoIds, Long vendedorId, UsuarioAutenticado actor) {
		exigirGerente(actor);
		List<Long> ids = prospectoIds == null ? List.of() : prospectoIds.stream().distinct().toList();
		if (ids.isEmpty() || ids.size() > MAXIMO_POR_VEZ) {
			throw new IllegalArgumentException("Elige de 1 a 100 prospectos");
		}
		Map<Long, Prospecto> encontrados = prospectoRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Prospecto::getId, Function.identity()));
		ids.stream().filter(id -> !encontrados.containsKey(id)).findFirst().ifPresent(id -> {
			throw new RecursoNoEncontradoException("No existe el prospecto " + id);
		});
		exigirVendedorActivo(vendedorId);
		List<Prospecto> elegidos = ids.stream().map(encontrados::get).toList();
		elegidos.forEach(prospecto -> prospecto.reasignarA(vendedorId));
		return elegidos.stream().map(ProspectoVista::de).toList();
	}

	private static void exigirGerente(UsuarioAutenticado actor) {
		if (!actor.tieneRol(Role.GERENTE_MARKETING)) {
			throw new AccessDeniedException("Solo el Gerente de marketing reasigna prospectos");
		}
	}

	private void exigirVendedorActivo(Long vendedorId) {
		if (!consultaVendedores.esVendedorActivo(vendedorId)) {
			throw new IllegalArgumentException("El usuario " + vendedorId + " no es un vendedor activo");
		}
	}
}
