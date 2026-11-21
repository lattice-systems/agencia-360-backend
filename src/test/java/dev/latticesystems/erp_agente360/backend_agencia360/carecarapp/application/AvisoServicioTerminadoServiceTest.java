package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush.Notificacion;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ServicioRegistrado;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AvisoServicioTerminadoServiceTest {

	@Test
	void avisaAlDuenoDelVehiculo() {
		VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
		NotificacionesService notificaciones = mock(NotificacionesService.class);
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(new VehiculoDeCliente(3L, "VIN", "Nissan", "March", 2026,
				null, null, null, new Propietario(9L, "Ana", null, null))));

		new AvisoServicioTerminadoService(vehiculos, notificaciones)
				.alRegistrarServicio(new ServicioRegistrado(77L, 3L));

		ArgumentCaptor<Notificacion> enviada = ArgumentCaptor.forClass(Notificacion.class);
		verify(notificaciones).avisar(eq(9L), enviada.capture());
		assertThat(enviada.getValue().datos()).containsEntry("tipo", "SERVICIO").containsEntry("servicioId", "77");
	}
}
