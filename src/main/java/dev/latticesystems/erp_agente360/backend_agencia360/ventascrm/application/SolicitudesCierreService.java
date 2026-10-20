package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.EstadoSolicitud;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.MotivoCierre;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.SolicitudCierre;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.SolicitudCierreRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de cierre (decisión del PO del 12 de octubre): desde Pago en
 * adelante el vendedor no cierra el prospecto; lo solicita con un motivo y el
 * Administrador del ERP lo aprueba o lo rechaza.
 */
@Service
public class SolicitudesCierreService {

	private final SolicitudCierreRepository solicitudCierreRepository;
	private final ProspectoRepository prospectoRepository;
	private final DirectorioUsuarios directorioUsuarios;
	private final Clock reloj;

	@Autowired
	public SolicitudesCierreService(SolicitudCierreRepository solicitudCierreRepository,
			ProspectoRepository prospectoRepository, DirectorioUsuarios directorioUsuarios) {
		this(solicitudCierreRepository, prospectoRepository, directorioUsuarios, Clock.systemUTC());
	}

	SolicitudesCierreService(SolicitudCierreRepository solicitudCierreRepository,
			ProspectoRepository prospectoRepository, DirectorioUsuarios directorioUsuarios, Clock reloj) {
		this.solicitudCierreRepository = solicitudCierreRepository;
		this.prospectoRepository = prospectoRepository;
		this.directorioUsuarios = directorioUsuarios;
		this.reloj = reloj;
	}

	/**
	 * El vendedor dueño del prospecto pide cerrarlo.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el prospecto no existe o es de otro vendedor (404)
	 */
	@Transactional
	public SolicitudCierreVista solicitar(Long prospectoId, MotivoCierre motivo, UsuarioAutenticado vendedor) {
		exigirRol(vendedor, Role.VENTAS, "Solo el vendedor solicita el cierre de sus prospectos");
		Prospecto prospecto = prospectoRepository.findByIdAndVendedorId(prospectoId, vendedor.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el prospecto " + prospectoId));
		SolicitudCierre solicitud = solicitudCierreRepository
				.save(SolicitudCierre.solicitar(prospecto, motivo, vendedor.id()));
		return vista(solicitud, nombres(List.of(solicitud)));
	}

	/**
	 * Solicitudes pendientes para el Administrador, de la más antigua a la más
	 * reciente.
	 */
	@Transactional(readOnly = true)
	public List<SolicitudCierreVista> pendientes(UsuarioAutenticado administrador) {
		exigirAdministrador(administrador);
		List<SolicitudCierre> solicitudes = solicitudCierreRepository.buscarPorEstado(EstadoSolicitud.PENDIENTE);
		Map<Long, String> nombres = nombres(solicitudes);
		return solicitudes.stream().map(solicitud -> vista(solicitud, nombres)).toList();
	}

	/** El Administrador acepta y el prospecto pasa a Cerrado. */
	@Transactional
	public SolicitudCierreVista aprobar(Long solicitudId, UsuarioAutenticado administrador) {
		return resolver(solicitudId, administrador, SolicitudCierre::aprobar);
	}

	/** El Administrador rechaza y el prospecto sigue en su etapa. */
	@Transactional
	public SolicitudCierreVista rechazar(Long solicitudId, UsuarioAutenticado administrador) {
		return resolver(solicitudId, administrador, SolicitudCierre::rechazar);
	}

	private SolicitudCierreVista resolver(Long solicitudId, UsuarioAutenticado administrador,
			ResolucionDeSolicitud resolucion) {
		exigirAdministrador(administrador);
		SolicitudCierre solicitud = solicitudCierreRepository.findById(solicitudId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud " + solicitudId));
		resolucion.aplicar(solicitud, administrador.id(), Instant.now(reloj));
		return vista(solicitud, nombres(List.of(solicitud)));
	}

	private Map<Long, String> nombres(List<SolicitudCierre> solicitudes) {
		return directorioUsuarios.nombresDe(solicitudes.stream().map(SolicitudCierre::getSolicitanteId).toList());
	}

	private static SolicitudCierreVista vista(SolicitudCierre solicitud, Map<Long, String> nombres) {
		Prospecto prospecto = solicitud.getProspecto();
		return new SolicitudCierreVista(solicitud.getId(), prospecto.getId(), prospecto.getNombre(),
				prospecto.getEtapa(), solicitud.getMotivo(), solicitud.getSolicitanteId(),
				nombres.get(solicitud.getSolicitanteId()), solicitud.getCreatedAt(), solicitud.getEstado());
	}

	private static void exigirAdministrador(UsuarioAutenticado actor) {
		exigirRol(actor, Role.ADMINISTRADOR, "Solo el Administrador resuelve las solicitudes de cierre");
	}

	private static void exigirRol(UsuarioAutenticado actor, Role rol, String mensaje) {
		if (!actor.tieneRol(rol)) {
			throw new AccessDeniedException(mensaje);
		}
	}

	/** Aprobar o rechazar, con quién y cuándo. */
	@FunctionalInterface
	private interface ResolucionDeSolicitud {
		void aplicar(SolicitudCierre solicitud, Long administradorId, Instant ahora);
	}
}
