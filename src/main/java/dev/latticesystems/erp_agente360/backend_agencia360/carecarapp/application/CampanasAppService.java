package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaSeguridad;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CampanaVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCampana;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CampanaSeguridadRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CampanaVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campañas de seguridad (V2-M10.02 a V2-M10.04). Una campaña aplica a un
 * vehículo por modelo, año y rango de VIN. Al consultarlas se crea la fila de
 * {@code campana_vehiculo} de las que aún no la tienen, y una agendada cuya
 * cita se canceló vuelve a estar abierta. Agendar usa la agenda de citas con
 * motivo {@code campana}. Publicar una campaña la cruza con los vehículos y
 * avisa a cada dueño con {@link AvisoAlCliente} {@code campana.nueva}.
 */
@Service
public class CampanasAppService {

	static final String EVENTO = "campana.nueva";

	private static final Comparator<CampanaApp> ORDEN = Comparator.comparing(CampanaApp::estado)
			.thenComparing(CampanaApp::gravedad, Comparator.reverseOrder());

	private final VehiculosDeClientes vehiculos;
	private final CampanaSeguridadRepository campanas;
	private final CampanaVehiculoRepository delVehiculo;
	private final CitaRepository citas;
	private final AgendaAppService agenda;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public CampanasAppService(VehiculosDeClientes vehiculos, CampanaSeguridadRepository campanas,
			CampanaVehiculoRepository delVehiculo, CitaRepository citas, AgendaAppService agenda,
			ApplicationEventPublisher eventos) {
		this(vehiculos, campanas, delVehiculo, citas, agenda, eventos, Clock.systemUTC());
	}

	CampanasAppService(VehiculosDeClientes vehiculos, CampanaSeguridadRepository campanas,
			CampanaVehiculoRepository delVehiculo, CitaRepository citas, AgendaAppService agenda,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.vehiculos = vehiculos;
		this.campanas = campanas;
		this.delVehiculo = delVehiculo;
		this.citas = citas;
		this.agenda = agenda;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * Las campañas del vehículo: abiertas primero (la más grave arriba), luego
	 * agendadas y atendidas.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 */
	@Transactional
	public List<CampanaApp> campanas(Long clienteId, Long vehiculoId) {
		return sincronizar(propio(clienteId, vehiculoId)).stream().sorted(ORDEN).toList();
	}

	/**
	 * Agenda la atención de una campaña abierta: la cita lleva motivo
	 * {@code campana} y la campaña queda agendada con esa cita (V2-M10.03).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no es del cliente o la campaña no aplica a él
	 *             (404)
	 * @throws ConflictoException
	 *             con {@code CAMPANA_NO_AGENDABLE} si ya está agendada o atendida,
	 *             o los de la agenda ({@code FRANJA_SIN_CUPO},
	 *             {@code CITA_DUPLICADA}) (409)
	 * @throws IllegalArgumentException
	 *             si la franja o la fecha no son válidas (400)
	 */
	@Transactional
	public CitaApp agendar(Long clienteId, Long vehiculoId, Long campanaId, Long franjaId, LocalDate fecha) {
		sincronizar(propio(clienteId, vehiculoId));
		CampanaVehiculo campana = delVehiculo.findByCampanaSeguridadIdAndVehiculoClienteId(campanaId, vehiculoId)
				.orElseThrow(() -> noAplica(campanaId));
		if (campana.getEstado() != EstadoCampana.ABIERTA) {
			throw new ConflictoException("La campaña ya está agendada o atendida", CodigosError.CAMPANA_NO_AGENDABLE);
		}
		CitaApp cita = agenda.agendar(clienteId, vehiculoId, franjaId, fecha, MotivoCita.CAMPANA);
		campana.agendar(cita.id());
		delVehiculo.save(campana);
		return cita;
	}

	/**
	 * Publica una campaña (personal de servicio, V2-M10.04): la cruza con los
	 * vehículos del modelo, año y rango de VIN y avisa a cada dueño.
	 *
	 * @throws IllegalArgumentException
	 *             si un dato no es válido o ya existe una campaña con ese código
	 *             (400)
	 */
	@Transactional
	public CampanaPublicadaApp publicar(CampanaSeguridad.Datos datos) {
		CampanaSeguridad nueva = CampanaSeguridad.publicar(datos, reloj.instant());
		if (campanas.existsByCodigo(nueva.getCodigo())) {
			throw new IllegalArgumentException("Ya existe la campaña " + nueva.getCodigo());
		}
		campanas.save(nueva);
		int afectados = 0;
		for (VehiculoDeCliente vehiculo : vehiculos.delModelo(nueva.getModelo(), nueva.getAnioDesde(),
				nueva.getAnioHasta())) {
			if (!nueva.aplicaA(vehiculo.modelo(), vehiculo.anio(), vehiculo.vin())) {
				continue;
			}
			CampanaVehiculo delAuto = new CampanaVehiculo(nueva.getId(), vehiculo.id());
			delAuto.marcarAvisada(reloj.instant());
			delVehiculo.save(delAuto);
			eventos.publishEvent(new AvisoAlCliente(vehiculo.propietario().id(), EVENTO, aviso(nueva, vehiculo.id())));
			afectados++;
		}
		return new CampanaPublicadaApp(nueva.getId(), nueva.getCodigo(), afectados);
	}

	private static RecursoNoEncontradoException noAplica(Long campanaId) {
		return new RecursoNoEncontradoException("La campaña " + campanaId + " no aplica al vehículo");
	}

	/**
	 * Los datos del aviso {@code campana.nueva}; {@code critica} hace que el push
	 * no se pueda silenciar.
	 */
	private static Map<String, Object> aviso(CampanaSeguridad campana, Long vehiculoId) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("vehiculoId", vehiculoId);
		datos.put("campanaId", campana.getId());
		datos.put("codigo", campana.getCodigo());
		datos.put("titulo", campana.getTitulo());
		datos.put("gravedad", campana.getGravedad().name());
		datos.put("critica", campana.getGravedad().critica());
		return datos;
	}

	/**
	 * El taller ya atendió la campaña en ese vehículo (personal de servicio).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la campaña no existe o no aplica al vehículo (404)
	 */
	@Transactional
	public CampanaApp atender(Long campanaId, Long vehiculoId) {
		CampanaSeguridad campana = campanas.findById(campanaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la campaña " + campanaId));
		CampanaVehiculo delAuto = delVehiculo.findByCampanaSeguridadIdAndVehiculoClienteId(campanaId, vehiculoId)
				.orElseThrow(() -> noAplica(campanaId));
		delAuto.atender();
		delVehiculo.save(delAuto);
		return CampanaApp.de(campana, delAuto);
	}

	private List<CampanaApp> sincronizar(VehiculoDeCliente vehiculo) {
		Map<Long, CampanaVehiculo> propias = delVehiculo.findByVehiculoClienteId(vehiculo.id()).stream()
				.collect(Collectors.toMap(CampanaVehiculo::getCampanaSeguridadId, Function.identity()));
		List<CampanaApp> resultado = new ArrayList<>();
		for (CampanaSeguridad campana : campanas.delModelo(vehiculo.modelo(), vehiculo.anio())) {
			if (!campana.aplicaA(vehiculo.modelo(), vehiculo.anio(), vehiculo.vin())) {
				continue;
			}
			CampanaVehiculo delAuto = propias.get(campana.getId());
			if (delAuto == null) {
				delAuto = delVehiculo.save(new CampanaVehiculo(campana.getId(), vehiculo.id()));
			} else if (delAuto.getEstado() == EstadoCampana.AGENDADA && citaCancelada(delAuto.getCitaId())) {
				delAuto.reabrir();
				delVehiculo.save(delAuto);
			}
			resultado.add(CampanaApp.de(campana, delAuto));
		}
		return resultado;
	}

	private boolean citaCancelada(Long citaId) {
		return citas.findById(citaId).map(cita -> cita.getEstado() == EstadoCita.CANCELADA).orElse(true);
	}

	private VehiculoDeCliente propio(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
	}
}
