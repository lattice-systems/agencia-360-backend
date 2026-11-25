package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Taller en vivo en la app (V2-M04.01 a V2-M04.07): las órdenes abiertas de los
 * vehículos del cliente, el detalle de una orden y la respuesta a sus trabajos
 * adicionales. Cuando la orden está lista para entrega trae también el horario
 * para recogerla. Una orden de un vehículo ajeno responde 404, como pide el
 * contrato.
 */
@Service
public class TallerAppService {

	private final VehiculosDeClientes vehiculos;
	private final TallerEnVivo taller;
	private final HorarioTaller horarioTaller;
	private final Clock reloj;

	@Autowired
	public TallerAppService(VehiculosDeClientes vehiculos, TallerEnVivo taller, HorarioTaller horarioTaller) {
		this(vehiculos, taller, horarioTaller, Clock.system(ServicioApp.ZONA_AGENCIA));
	}

	TallerAppService(VehiculosDeClientes vehiculos, TallerEnVivo taller, HorarioTaller horarioTaller, Clock reloj) {
		this.vehiculos = vehiculos;
		this.taller = taller;
		this.horarioTaller = horarioTaller;
		this.reloj = reloj;
	}

	/**
	 * Órdenes sin entregar de los vehículos del cliente, la más reciente primero.
	 */
	public List<OrdenEnCursoApp> enCurso(Long clienteId) {
		Map<Long, VehiculoDeCliente> propios = propios(clienteId);
		return taller.enCurso(propios.keySet()).stream().map(orden -> OrdenEnCursoApp.de(orden,
				descripcion(propios.get(orden.vehiculoId())), horarioSiEstaLista(orden.etapa()))).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe o es de un vehículo ajeno (404)
	 */
	public OrdenEnVivoApp orden(Long clienteId, Long ordenId) {
		Map<Long, VehiculoDeCliente> propios = propios(clienteId);
		return taller
				.orden(ordenId).filter(orden -> propios.containsKey(orden.vehiculoId())).map(orden -> OrdenEnVivoApp
						.de(orden, descripcion(propios.get(orden.vehiculoId())), horarioSiEstaLista(orden.etapa())))
				.orElseThrow(() -> noExiste(ordenId));
	}

	/**
	 * Aprueba o rechaza un trabajo adicional con la firma del cliente.
	 *
	 * @param firmaBase64
	 *            la firma en PNG codificada en Base64
	 * @throws RecursoNoEncontradoException
	 *             si la orden o el trabajo no existen o son de un vehículo ajeno
	 *             (404)
	 * @throws IllegalArgumentException
	 *             si la firma no es un PNG en Base64 (400)
	 */
	public OrdenEnVivoApp responder(Long clienteId, Long ordenId, Long adicionalId, boolean aprobado,
			String firmaBase64) {
		Map<Long, VehiculoDeCliente> propios = propios(clienteId);
		Long vehiculoId = taller.orden(ordenId).map(TallerEnVivo.OrdenEnVivo::vehiculoId).filter(propios::containsKey)
				.orElseThrow(() -> noExiste(ordenId));
		byte[] firma;
		try {
			firma = Base64.getDecoder().decode(Objects.requireNonNullElse(firmaBase64, ""));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("La firma debe venir en Base64", ex);
		}
		TallerEnVivo.OrdenEnVivo respondida = taller.responder(ordenId, adicionalId, aprobado, firma, clienteId);
		return OrdenEnVivoApp.de(respondida, descripcion(propios.get(vehiculoId)),
				horarioSiEstaLista(respondida.etapa()));
	}

	private HorarioEntregaApp horarioSiEstaLista(EtapaTaller etapa) {
		if (etapa != EtapaTaller.LISTO_PARA_ENTREGA) {
			return null;
		}
		return HorarioEntregaApp.siguiente(horarioTaller.franjasPorDia(), LocalDateTime.now(reloj)).orElse(null);
	}

	private Map<Long, VehiculoDeCliente> propios(Long clienteId) {
		return vehiculos.deCliente(clienteId).stream()
				.collect(Collectors.toMap(VehiculoDeCliente::id, Function.identity()));
	}

	static String descripcion(VehiculoDeCliente vehiculo) {
		return Stream.of(vehiculo.marca(), vehiculo.modelo(), String.valueOf(vehiculo.anio()))
				.filter(parte -> parte != null && !parte.isBlank()).collect(Collectors.joining(" "));
	}

	private static RecursoNoEncontradoException noExiste(Long ordenId) {
		return new RecursoNoEncontradoException("No existe la orden " + ordenId);
	}
}
