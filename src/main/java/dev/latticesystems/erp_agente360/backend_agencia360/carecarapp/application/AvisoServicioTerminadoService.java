package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush.Notificacion;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ServicioRegistrado;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Avisa por push al dueño del vehículo que su servicio quedó terminado (APP-6).
 */
@Service
public class AvisoServicioTerminadoService {

	private final VehiculosDeClientes vehiculos;
	private final NotificacionesService notificaciones;

	public AvisoServicioTerminadoService(VehiculosDeClientes vehiculos, NotificacionesService notificaciones) {
		this.vehiculos = vehiculos;
		this.notificaciones = notificaciones;
	}

	@ApplicationModuleListener
	public void alRegistrarServicio(ServicioRegistrado registrado) {
		vehiculos.buscarPorId(registrado.vehiculoId())
				.ifPresent(vehiculo -> notificaciones.avisar(vehiculo.propietario().id(),
						new Notificacion("Tu vehículo está listo",
								"Terminamos el servicio de tu " + vehiculo.modelo() + ". Ya puedes ver el ticket.",
								Map.of("tipo", "SERVICIO", "servicioId", String.valueOf(registrado.servicioId()),
										"vehiculoId", String.valueOf(registrado.vehiculoId())))));
	}
}
