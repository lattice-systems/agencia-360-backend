package dev.latticesystems.erp_agente360.backend_agencia360.clientes.application;

import dev.latticesystems.erp_agente360.backend_agencia360.clientes.ClienteListoParaLaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.domain.Cliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.infrastructure.ClienteRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ConsultaUnidades;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ConsultaUnidades.UnidadDelProspecto;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * CRM-1.3: cuando el vendedor finaliza un prospecto, registra al cliente y le
 * asigna la unidad que eligió Facturación. Si el cliente ya existía con ese
 * correo (por ejemplo, compra su segundo auto), solo le agrega el vehículo. Si
 * tiene correo, avisa con {@link ClienteListoParaLaApp} para que se abra su
 * cuenta de la app.
 *
 * <p>
 * Corre después del commit del cambio de etapa, en su propia transacción. Si el
 * evento llega dos veces (Modulith reintenta las publicaciones pendientes), no
 * se duplica nada.
 */
@Service
public class RegistroAlFinalizarService {

	private static final Logger LOG = LoggerFactory.getLogger(RegistroAlFinalizarService.class);

	private final ClienteRepository clienteRepository;
	private final ConsultaUnidades consultaUnidades;
	private final ApplicationEventPublisher eventos;

	public RegistroAlFinalizarService(ClienteRepository clienteRepository, ConsultaUnidades consultaUnidades,
			ApplicationEventPublisher eventos) {
		this.clienteRepository = clienteRepository;
		this.consultaUnidades = consultaUnidades;
		this.eventos = eventos;
	}

	@ApplicationModuleListener
	public void alFinalizar(ProspectoFinalizado finalizado) {
		String correo = correoValido(finalizado);
		Cliente cliente = clienteRepository.findByProspectoId(finalizado.prospectoId())
				.or(() -> correo == null ? Optional.empty() : clienteRepository.findByCorreo(correo))
				.orElseGet(() -> clienteRepository
						.save(Cliente.registrar(finalizado.nombre(), correo, null, finalizado.prospectoId())));

		consultaUnidades.unidadDelProspecto(finalizado.prospectoId()).ifPresentOrElse(
				unidad -> asignar(cliente, unidad),
				() -> LOG.warn("El prospecto {} se finalizó sin unidad elegida; el cliente queda sin vehículo",
						finalizado.prospectoId()));

		if (cliente.getCorreo() != null) {
			eventos.publishEvent(new ClienteListoParaLaApp(cliente.getId()));
		}
	}

	private void asignar(Cliente cliente, UnidadDelProspecto unidad) {
		boolean yaEsSuyo = cliente.getVehiculos().stream().anyMatch(v -> v.getVin().equals(unidad.vin()));
		if (!yaEsSuyo && clienteRepository.existeVehiculoConVin(unidad.vin())) {
			LOG.warn("La unidad {} ya está asignada a otro cliente; no se reasigna", unidad.vehiculoId());
			return;
		}
		cliente.asignarVehiculo(unidad.vehiculoId(), unidad.vin(), unidad.modelo(), unidad.anio());
	}

	/**
	 * Un correo sin formato no debe trabar el evento: el cliente queda sin correo.
	 */
	private static String correoValido(ProspectoFinalizado finalizado) {
		try {
			return Cliente.normalizarCorreo(finalizado.correoCliente());
		} catch (IllegalArgumentException ex) {
			LOG.warn("El prospecto {} se finalizó con un correo no válido; el cliente queda sin correo",
					finalizado.prospectoId());
			return null;
		}
	}
}
