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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de asistencia (SOS) del cliente (V2-M11.01). Cada cliente tiene a
 * lo más una solicitud abierta a la vez.
 */
@Service
public class AsistenciaAppService {

	private static final EnumSet<EstadoAsistencia> ABIERTAS = EnumSet.of(EstadoAsistencia.RECIBIDA,
			EstadoAsistencia.EN_CAMINO);

	private final SolicitudAsistenciaRepository repositorio;
	private final VehiculosDeClientes vehiculos;
	private final Clock reloj;

	@Autowired
	public AsistenciaAppService(SolicitudAsistenciaRepository repositorio, VehiculosDeClientes vehiculos) {
		this(repositorio, vehiculos, Clock.systemUTC());
	}

	AsistenciaAppService(SolicitudAsistenciaRepository repositorio, VehiculosDeClientes vehiculos, Clock reloj) {
		this.repositorio = repositorio;
		this.vehiculos = vehiculos;
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
}
