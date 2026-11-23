package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.OrdenServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.OrdenServicioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expediente del vehículo del cliente (ERP-3.0). Servicio y el Administrador lo
 * consultan; solo Servicio corrige placas y kilometraje y registra servicios.
 * Servicio no crea clientes: los vehículos vienen del CRM.
 */
@Service
public class ExpedienteService {

	/** La fecha del servicio se compara con el día en la agencia, no en UTC. */
	static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	private static final String SIN_NOMBRE = "Sin nombre";

	private final VehiculosDeClientes vehiculos;
	private final OrdenServicioRepository ordenServicioRepository;
	private final DirectorioUsuarios directorioUsuarios;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public ExpedienteService(VehiculosDeClientes vehiculos, OrdenServicioRepository ordenServicioRepository,
			DirectorioUsuarios directorioUsuarios, ApplicationEventPublisher eventos) {
		this(vehiculos, ordenServicioRepository, directorioUsuarios, eventos, Clock.system(ZONA_AGENCIA));
	}

	ExpedienteService(VehiculosDeClientes vehiculos, OrdenServicioRepository ordenServicioRepository,
			DirectorioUsuarios directorioUsuarios, ApplicationEventPublisher eventos, Clock reloj) {
		this.vehiculos = vehiculos;
		this.ordenServicioRepository = ordenServicioRepository;
		this.directorioUsuarios = directorioUsuarios;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** @return los vehículos cuyo VIN contiene el texto (al menos 5 caracteres) */
	public List<Expediente.Encontrado> buscar(String vin) {
		return vehiculos.buscarPorVin(vin).stream().map(Expediente.Encontrado::de).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe (404)
	 */
	@Transactional(readOnly = true)
	public Expediente expediente(Long vehiculoId) {
		VehiculoDeCliente vehiculo = vehiculo(vehiculoId);
		List<OrdenServicio> servicios = ordenServicioRepository
				.findByVehiculoClienteIdOrderByFechaDescIdDesc(vehiculoId);
		Map<Long, String> nombres = directorioUsuarios
				.nombresDe(servicios.stream().map(OrdenServicio::getAtendidoPor).distinct().toList());
		return new Expediente(Expediente.Vehiculo.de(vehiculo), vehiculo.propietario(),
				servicios.stream().map(servicio -> ServicioResumen.de(servicio, nombreDe(nombres, servicio))).toList());
	}

	/**
	 * Corrige las placas y el kilometraje del vehículo.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe (404)
	 * @throws IllegalArgumentException
	 *             si las placas o el kilometraje no son válidos (400)
	 */
	public Expediente.Vehiculo corregir(Long vehiculoId, String placas, Integer kilometraje, String color) {
		return Expediente.Vehiculo.de(vehiculos.corregir(vehiculoId, placas, kilometraje, color));
	}

	/**
	 * Registra un servicio a nombre de quien lo atiende.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe (404)
	 * @throws IllegalArgumentException
	 *             si un dato no es válido (400)
	 */
	@Transactional
	public ServicioDetalle registrar(Long vehiculoId, NuevoServicio nuevo, UsuarioAutenticado actor) {
		VehiculoDeCliente vehiculo = vehiculo(vehiculoId);
		OrdenServicio servicio = ordenServicioRepository.save(OrdenServicio.registrar(vehiculoId, nuevo.fecha(),
				nuevo.kilometraje(), actor.id(), nuevo.renglones(), ZonedDateTime.now(reloj)));
		eventos.publishEvent(new OrdenServicioCambiada(servicio.getId(), vehiculoId, vehiculo.propietario().id(),
				servicio.getEstatus()));
		return ServicioDetalle.de(servicio, nombreDe(directorioUsuarios.nombresDe(List.of(actor.id())), servicio));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el servicio no existe o no es de ese vehículo (404)
	 */
	@Transactional(readOnly = true)
	public ServicioDetalle detalle(Long vehiculoId, Long servicioId) {
		OrdenServicio servicio = ordenServicioRepository.findByIdAndVehiculoClienteId(servicioId, vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"El vehículo " + vehiculoId + " no tiene el servicio " + servicioId));
		return ServicioDetalle.de(servicio,
				nombreDe(directorioUsuarios.nombresDe(List.of(servicio.getAtendidoPor())), servicio));
	}

	private VehiculoDeCliente vehiculo(Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
	}

	private static String nombreDe(Map<Long, String> nombres, OrdenServicio servicio) {
		return nombres.getOrDefault(servicio.getAtendidoPor(), SIN_NOMBRE);
	}
}
