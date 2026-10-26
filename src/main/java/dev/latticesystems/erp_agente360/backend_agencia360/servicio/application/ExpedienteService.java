package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.Servicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.ServicioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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
	private final ServicioRepository servicioRepository;
	private final DirectorioUsuarios directorioUsuarios;
	private final Clock reloj;

	@Autowired
	public ExpedienteService(VehiculosDeClientes vehiculos, ServicioRepository servicioRepository,
			DirectorioUsuarios directorioUsuarios) {
		this(vehiculos, servicioRepository, directorioUsuarios, Clock.system(ZONA_AGENCIA));
	}

	ExpedienteService(VehiculosDeClientes vehiculos, ServicioRepository servicioRepository,
			DirectorioUsuarios directorioUsuarios, Clock reloj) {
		this.vehiculos = vehiculos;
		this.servicioRepository = servicioRepository;
		this.directorioUsuarios = directorioUsuarios;
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
		List<Servicio> servicios = servicioRepository.findByVehiculoClienteIdOrderByFechaDescIdDesc(vehiculoId);
		Map<Long, String> nombres = directorioUsuarios
				.nombresDe(servicios.stream().map(Servicio::getAtendidoPor).distinct().toList());
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
	public Expediente.Vehiculo corregir(Long vehiculoId, String placas, Integer kilometraje) {
		return Expediente.Vehiculo.de(vehiculos.corregir(vehiculoId, placas, kilometraje));
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
		vehiculo(vehiculoId);
		Servicio servicio = servicioRepository.save(Servicio.registrar(vehiculoId, nuevo.fecha(), nuevo.kilometraje(),
				actor.id(), nuevo.renglones(), LocalDate.now(reloj)));
		return ServicioDetalle.de(servicio, nombreDe(directorioUsuarios.nombresDe(List.of(actor.id())), servicio));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el servicio no existe o no es de ese vehículo (404)
	 */
	@Transactional(readOnly = true)
	public ServicioDetalle detalle(Long vehiculoId, Long servicioId) {
		Servicio servicio = servicioRepository.obtenerConRenglones(vehiculoId, servicioId)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"El vehículo " + vehiculoId + " no tiene el servicio " + servicioId));
		return ServicioDetalle.de(servicio,
				nombreDe(directorioUsuarios.nombresDe(List.of(servicio.getAtendidoPor())), servicio));
	}

	private VehiculoDeCliente vehiculo(Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
	}

	private static String nombreDe(Map<Long, String> nombres, Servicio servicio) {
		return nombres.getOrDefault(servicio.getAtendidoPor(), SIN_NOMBRE);
	}
}
