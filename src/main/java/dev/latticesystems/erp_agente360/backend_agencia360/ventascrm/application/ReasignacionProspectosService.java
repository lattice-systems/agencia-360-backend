package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reasignación de prospectos entre vendedores (CRM-0.1). Solo el Gerente de
 * marketing reasigna, y el nuevo responsable debe ser un usuario activo con rol
 * VENTAS.
 */
@Service
public class ReasignacionProspectosService {

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
		if (!actor.tieneRol(Role.GERENTE_MARKETING)) {
			throw new AccessDeniedException("Solo el Gerente de marketing reasigna prospectos");
		}
		Prospecto prospecto = prospectoRepository.findById(prospectoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
		if (!consultaVendedores.esVendedorActivo(vendedorId)) {
			throw new IllegalArgumentException("El usuario " + vendedorId + " no es un vendedor activo");
		}
		prospecto.reasignarA(vendedorId);
		return ProspectoVista.de(prospecto);
	}
}
