package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ActividadEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ActividadEnVivoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de la Live Activity de una orden en la pantalla bloqueada
 * (V2-M25.05): la app manda el push token que le da ActivityKit y el servidor
 * lo usa para actualizar o terminar la actividad aunque la app esté cerrada.
 * Una orden de un vehículo ajeno responde 404, como el resto del taller en
 * vivo.
 */
@Service
public class ActividadesEnVivoAppService {

	private final VehiculosDeClientes vehiculos;
	private final TallerEnVivo taller;
	private final ActividadEnVivoRepository actividades;

	@Autowired
	public ActividadesEnVivoAppService(VehiculosDeClientes vehiculos, TallerEnVivo taller,
			ActividadEnVivoRepository actividades) {
		this.vehiculos = vehiculos;
		this.taller = taller;
		this.actividades = actividades;
	}

	/**
	 * Guarda el token de la actividad. Es idempotente: repetir el mismo token no
	 * duplica y uno dado de baja se reactiva.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe o es de un vehículo ajeno (404)
	 * @throws ConflictoException
	 *             con {@code ORDEN_CERRADA} si la orden ya se entregó (409)
	 */
	@Transactional
	public void registrar(Long clienteId, Long ordenId, String pushToken) {
		OrdenEnVivo orden = propia(clienteId, ordenId);
		if (orden.estatus() == EstatusOrden.ENTREGADO) {
			throw new ConflictoException("La orden ya se entregó", CodigosError.ORDEN_CERRADA);
		}
		ActividadEnVivo actividad = actividades.findByOrdenServicioIdAndPushToken(ordenId, pushToken)
				.orElseGet(() -> actividades.save(new ActividadEnVivo(clienteId, ordenId, pushToken)));
		actividad.reactivar();
	}

	/**
	 * Da de baja la actividad del cliente en esa orden; sin token, todas las que
	 * tenga ahí. Es idempotente.
	 *
	 * @param pushToken
	 *            el de la actividad a quitar, o {@code null} para todas
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe o es de un vehículo ajeno (404)
	 */
	@Transactional
	public void quitar(Long clienteId, Long ordenId, String pushToken) {
		propia(clienteId, ordenId);
		actividades.findByClienteIdAndOrdenServicioIdAndActivaTrue(clienteId, ordenId).stream()
				.filter(actividad -> pushToken == null || pushToken.equals(actividad.getPushToken()))
				.forEach(ActividadEnVivo::terminar);
	}

	private OrdenEnVivo propia(Long clienteId, Long ordenId) {
		Set<Long> propios = vehiculos.deCliente(clienteId).stream().map(VehiculoDeCliente::id)
				.collect(Collectors.toSet());
		return taller.orden(ordenId).filter(orden -> propios.contains(orden.vehiculoId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la orden " + ordenId));
	}
}
