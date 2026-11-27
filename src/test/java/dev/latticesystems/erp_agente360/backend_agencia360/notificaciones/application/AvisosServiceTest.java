package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.Dispositivos.DispositivoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.MensajePush;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.ResultadoEnvio;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * V2-M06.07: un aviso se guarda en la bandeja y se manda a los dispositivos
 * activos; un fallo del canal nunca llega a quien causó el aviso.
 */
class AvisosServiceTest {

	private static final Map<String, Object> RECORDATORIO = Map.of("vehiculoId", 7L, "servicioId", 3L, "nombre",
			"Servicio de 10,000 km", "faltanKm", 850, "faltanDias", 40, "motivo", "km");

	private Dispositivos dispositivos;
	private Bandeja bandeja;
	private EnvioPush envio;
	private AvisosService servicio;

	@BeforeEach
	void preparar() {
		dispositivos = mock(Dispositivos.class);
		bandeja = mock(Bandeja.class);
		envio = mock(EnvioPush.class);
		servicio = new AvisosService(new RedactorDeAvisos(), dispositivos, bandeja, envio, Runnable::run);
		when(envio.enviar(any())).thenReturn(ResultadoEnvio.ENVIADO);
	}

	@Test
	void guardaElAvisoYLoMandaACadaDispositivoActivo() {
		when(dispositivos.activosDe(5L)).thenReturn(
				List.of(new DispositivoDeCliente(1L, "token-a", "es"), new DispositivoDeCliente(2L, "token-b", "es")));

		servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO);

		ArgumentCaptor<MensajePush> enviados = ArgumentCaptor.forClass(MensajePush.class);
		verify(envio, org.mockito.Mockito.times(2)).enviar(enviados.capture());
		assertThat(enviados.getAllValues()).extracting(MensajePush::token).containsExactly("token-a", "token-b");
		MensajePush mensaje = enviados.getAllValues().get(0);
		assertThat(mensaje.titulo()).isEqualTo("Se acerca tu próximo servicio");
		assertThat(mensaje.cuerpo())
				.isEqualTo("Servicio de 10,000 km: faltan unos 850 km. Agenda tu cita cuando quieras.");
		verify(bandeja).guardar(any(), any(), any(), any());
	}

	@Test
	void elPayloadLlevaSoloElEventoElDestinoYLosIds() {
		when(dispositivos.activosDe(5L)).thenReturn(List.of(new DispositivoDeCliente(1L, "token-a", "es")));

		servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO);

		ArgumentCaptor<MensajePush> enviado = ArgumentCaptor.forClass(MensajePush.class);
		verify(envio).enviar(enviado.capture());
		assertThat(enviado.getValue().datos()).containsOnlyKeys("evento", "destino", "vehiculoId", "servicioId")
				.containsEntry("evento", "mantenimiento.recordatorio").containsEntry("destino", "/mantenimiento/7")
				.containsEntry("vehiculoId", "7").containsEntry("servicioId", "3");
	}

	@Test
	void guardaEnLaBandejaAunqueNoHayaDispositivos() {
		when(dispositivos.activosDe(5L)).thenReturn(List.of());

		servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO);

		verify(bandeja).guardar(any(), any(), any(), any());
		verifyNoInteractions(envio);
	}

	@Test
	void unTokenInvalidoDesactivaElDispositivoYSeSigueConLosDemas() {
		when(dispositivos.activosDe(5L)).thenReturn(List.of(new DispositivoDeCliente(1L, "token-muerto", "es"),
				new DispositivoDeCliente(2L, "token-b", "es")));
		when(envio.enviar(new MensajePush("token-muerto", "Se acerca tu próximo servicio",
				"Servicio de 10,000 km: faltan unos 850 km. Agenda tu cita cuando quieras.",
				Map.of("evento", "mantenimiento.recordatorio", "destino", "/mantenimiento/7", "vehiculoId", "7",
						"servicioId", "3"))))
				.thenReturn(ResultadoEnvio.TOKEN_INVALIDO);

		servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO);

		verify(dispositivos).desactivar(1L);
		verify(dispositivos, never()).desactivar(2L);
		verify(envio, org.mockito.Mockito.times(2)).enviar(any());
	}

	@Test
	void unFalloDelCanalNoSePropagaNiDesactivaElDispositivo() {
		when(dispositivos.activosDe(5L)).thenReturn(List.of(new DispositivoDeCliente(1L, "token-a", "es")));
		when(envio.enviar(any())).thenThrow(new IllegalStateException("FCM caído"));

		assertThatCode(() -> servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO))
				.doesNotThrowAnyException();

		verify(dispositivos, never()).desactivar(any());
	}

	@Test
	void unFalloAlGuardarLaBandejaNoImpideElPush() {
		when(dispositivos.activosDe(5L)).thenReturn(List.of(new DispositivoDeCliente(1L, "token-a", "es")));
		doThrow(new IllegalStateException("base caída")).when(bandeja).guardar(any(), any(), any(), any());

		assertThatCode(() -> servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO))
				.doesNotThrowAnyException();

		verify(envio).enviar(any());
	}

	@Test
	void unFalloAlBuscarLosDispositivosNoSePropaga() {
		when(dispositivos.activosDe(5L)).thenThrow(new IllegalStateException("base caída"));

		assertThatCode(() -> servicio.avisar(5L, "mantenimiento.recordatorio", RECORDATORIO))
				.doesNotThrowAnyException();
	}

	@Test
	void unEventoSinTextoNoGuardaNiEnvia() {
		servicio.avisar(5L, "traslado.ubicacion", Map.of("trasladoId", 9L));

		verifyNoInteractions(bandeja, envio, dispositivos);
	}
}
