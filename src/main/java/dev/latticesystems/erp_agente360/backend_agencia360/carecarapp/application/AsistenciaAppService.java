package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoAsistencia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.SolicitudAsistencia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoAsistencia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.SolicitudAsistenciaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de asistencia (SOS) del cliente (V2-M11.01) y su seguimiento
 * (V2-M11.04). Cada cliente tiene a lo más una solicitud abierta a la vez.
 */
@Service
public class AsistenciaAppService {

	/** Nombre del aviso en vivo que recibe el cliente. */
	public static final String EVENTO_ESTADO = "asistencia.estado";

	private static final EnumSet<EstadoAsistencia> ABIERTAS = EnumSet.of(EstadoAsistencia.RECIBIDA,
			EstadoAsistencia.EN_CAMINO);

	private final SolicitudAsistenciaRepository repositorio;
	private final VehiculosDeClientes vehiculos;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public AsistenciaAppService(SolicitudAsistenciaRepository repositorio, VehiculosDeClientes vehiculos,
			ApplicationEventPublisher eventos) {
		this(repositorio, vehiculos, eventos, Clock.systemUTC());
	}

	AsistenciaAppService(SolicitudAsistenciaRepository repositorio, VehiculosDeClientes vehiculos,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.repositorio = repositorio;
		this.vehiculos = vehiculos;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * Crea la solicitud en estado {@code recibida}.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta el vehículo o el tipo, la ubicación es incompleta o está
	 *             fuera de rango o el comentario es muy largo (400)
	 * @throws ConflictoException
	 *             con {@code SOLICITUD_ABIERTA} si el cliente ya tiene una
	 *             solicitud abierta (409)
	 */
	@Transactional
	public SolicitudAsistenciaApp solicitar(Long clienteId, Long vehiculoId, TipoAsistencia tipo, BigDecimal lat,
			BigDecimal lng, String comentario) {
		if (vehiculoId == null) {
			throw new IllegalArgumentException("El vehículo es obligatorio");
		}
		vehiculos.buscarPorId(vehiculoId).filter(propio -> propio.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		SolicitudAsistencia nueva = SolicitudAsistencia.recibir(clienteId, vehiculoId, tipo, lat, lng, comentario,
				reloj.instant());
		if (activa(clienteId).isPresent()) {
			throw new ConflictoException("Ya tienes una solicitud de asistencia abierta",
					CodigosError.SOLICITUD_ABIERTA);
		}
		return SolicitudAsistenciaApp.de(repositorio.saveAndFlush(nueva));
	}

	/** La solicitud abierta del cliente, si tiene. */
	@Transactional(readOnly = true)
	public Optional<SolicitudAsistenciaApp> activa(Long clienteId) {
		return repositorio.findFirstByClienteIdAndEstadoInOrderByIdDesc(clienteId, ABIERTAS)
				.map(SolicitudAsistenciaApp::de);
	}

	/**
	 * Una solicitud del cliente, abierta o cerrada.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si no existe o es de otro cliente (404)
	 */
	@Transactional(readOnly = true)
	public SolicitudAsistenciaApp obtener(Long clienteId, Long solicitudId) {
		return repositorio.findByIdAndClienteId(solicitudId, clienteId).map(SolicitudAsistenciaApp::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud " + solicitudId));
	}

	/**
	 * El personal mueve la solicitud y el cliente dueño recibe el aviso en vivo.
	 *
	 * @param estado
	 *            {@code en_camino}, {@code atendida} o {@code cancelada}
	 * @throws IllegalArgumentException
	 *             si el estado falta o no existe (400)
	 * @throws RecursoNoEncontradoException
	 *             si la solicitud no existe (404)
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si el paso no está permitido
	 *             (409)
	 */
	@Transactional
	public SolicitudAsistenciaApp cambiarEstado(Long solicitudId, String estado) {
		if (estado == null) {
			throw new IllegalArgumentException("El estado es obligatorio");
		}
		EstadoAsistencia nuevo = EstadoAsistencia.desde(estado);
		SolicitudAsistencia solicitud = repositorio.findById(solicitudId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud " + solicitudId));
		solicitud.cambiarEstado(nuevo, reloj.instant());
		SolicitudAsistencia guardada = repositorio.saveAndFlush(solicitud);
		eventos.publishEvent(new AvisoAlCliente(guardada.getClienteId(), EVENTO_ESTADO,
				Map.of("solicitudId", guardada.getId(), "estado", nuevo.valor())));
		return SolicitudAsistenciaApp.de(guardada);
	}
}
