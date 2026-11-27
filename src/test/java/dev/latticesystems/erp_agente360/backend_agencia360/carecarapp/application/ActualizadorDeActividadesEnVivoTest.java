package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.PushApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioApns.ResultadoApns;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ActividadEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ActividadEnVivoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.Adicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ActualizadorDeActividadesEnVivoTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");
	private static final Instant ENTREGA = Instant.parse("2026-12-02T00:00:00Z");

	private final EnvioApnsFake apns = new EnvioApnsFake();
	private ActividadEnVivoRepository actividades;
	private TallerEnVivo taller;
	private ActualizadorDeActividadesEnVivo actualizador;

	private static ConfiguracionApns configuracion(String bundleId) {
		return new ConfiguracionApns("KEY1234567", "ABCDE12345", "cDhi", bundleId, true);
	}

	private static ActividadEnVivo actividad(String pushToken) {
		ActividadEnVivo actividad = new ActividadEnVivo(7L, 10L, pushToken);
		ReflectionTestUtils.setField(actividad, "id", 1L);
		return actividad;
	}

	private static OrdenEnVivo orden(EstatusOrden estatus, EtapaTaller etapa, Instant entrega,
			List<Adicional> adicionales) {
		return new OrdenEnVivo(10L, 3L, estatus, etapa, entrega, "Laura", etapa.avance(), List.of(), List.of(),
				List.of(), adicionales, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
	}

	private static Adicional adicional(EstadoAdicional estado) {
		return new Adicional(1L, "Balatas", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, estado, null);
	}

	private static OrdenServicioCambiada cambio(EtapaTaller etapa, MotivoCambio motivo) {
		return new OrdenServicioCambiada(10L, 3L, 7L, etapa.estatus(), etapa, motivo);
	}

	@BeforeEach
	void preparar() {
		actividades = mock(ActividadEnVivoRepository.class);
		taller = mock(TallerEnVivo.class);
		actualizador = nuevo(configuracion("mx.carecar.app"));
	}

	private ActualizadorDeActividadesEnVivo nuevo(ConfiguracionApns configuracion) {
		return new ActualizadorDeActividadesEnVivo(configuracion, apns, actividades, taller,
				mock(PlatformTransactionManager.class), Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	private JsonNode carga(int posicion) throws Exception {
		return JSON.readTree(apns.enviados().get(posicion).carga());
	}

	@Test
	void alCambiarDeEtapaActualizaCadaActividadConElEstadoNuevo() throws Exception {
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L))
				.thenReturn(List.of(actividad("tokenA"), actividad("tokenB")));
		when(taller.orden(10L)).thenReturn(Optional.of(orden(EstatusOrden.EN_PROCESO, EtapaTaller.EN_REPARACION,
				ENTREGA, List.of(adicional(EstadoAdicional.PENDIENTE), adicional(EstadoAdicional.APROBADO)))));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.EN_REPARACION, MotivoCambio.ETAPA));

		assertThat(apns.enviados()).extracting(PushApns::token).containsExactlyInAnyOrder("tokenA", "tokenB");
		PushApns push = apns.enviados().getFirst();
		assertThat(push.topic()).isEqualTo("mx.carecar.app.push-type.liveactivity");
		assertThat(push.sandbox()).isTrue();
		JsonNode aps = carga(0).path("aps");
		assertThat(aps.path("event").asText()).isEqualTo("update");
		assertThat(aps.path("timestamp").asLong()).isEqualTo(AHORA.getEpochSecond());
		JsonNode estado = aps.path("content-state");
		assertThat(estado.path("etapa").asText()).isEqualTo("EN_REPARACION");
		assertThat(estado.path("estatus").asText()).isEqualTo("EN_PROCESO");
		assertThat(estado.path("avance").asInt()).isEqualTo(EtapaTaller.EN_REPARACION.avance());
		assertThat(estado.path("entregaEstimada").asLong()).isEqualTo(ENTREGA.getEpochSecond());
		assertThat(estado.path("adicionalesPendientes").asInt()).isEqualTo(1);
		assertThat(aps.path("alert").path("body").asText()).contains("En reparación");
		assertThat(aps.has("dismissal-date")).isFalse();
	}

	@Test
	void sinEntregaEstimadaElEstadoNoLaTrae() throws Exception {
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenReturn(List.of(actividad("tokenA")));
		when(taller.orden(10L))
				.thenReturn(Optional.of(orden(EstatusOrden.RECIBIDO, EtapaTaller.RECIBIDO, null, List.of())));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.RECIBIDO, MotivoCambio.APERTURA));

		assertThat(carga(0).at("/aps/content-state").has("entregaEstimada")).isFalse();
	}

	@Test
	void unTrabajoAdicionalActualizaSinAlertarAlCliente() throws Exception {
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenReturn(List.of(actividad("tokenA")));
		when(taller.orden(10L)).thenReturn(Optional.of(orden(EstatusOrden.EN_PROCESO, EtapaTaller.ESPERANDO_APROBACION,
				ENTREGA, List.of(adicional(EstadoAdicional.PENDIENTE)))));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.ESPERANDO_APROBACION, MotivoCambio.ADICIONAL));

		assertThat(carga(0).at("/aps/content-state/adicionalesPendientes").asInt()).isEqualTo(1);
		assertThat(carga(0).at("/aps").has("alert")).isFalse();
	}

	@Test
	void alEntregarElAutoTerminaLaActividadYLaDaDeBaja() throws Exception {
		ActividadEnVivo enVivo = actividad("tokenA");
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenReturn(List.of(enVivo));
		when(taller.orden(10L))
				.thenReturn(Optional.of(orden(EstatusOrden.ENTREGADO, EtapaTaller.ENTREGADO, ENTREGA, List.of())));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.ENTREGADO, MotivoCambio.ETAPA));

		JsonNode aps = carga(0).path("aps");
		assertThat(aps.path("event").asText()).isEqualTo("end");
		assertThat(aps.path("content-state").path("etapa").asText()).isEqualTo("ENTREGADO");
		assertThat(aps.path("dismissal-date").asLong()).isEqualTo(AHORA.plusSeconds(900).getEpochSecond());
		assertThat(enVivo.isActiva()).isFalse();
		verify(actividades).save(enVivo);
	}

	@Test
	void unTokenMuertoDejaDeRecibirPushes() {
		ActividadEnVivo muerta = actividad("muerto");
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenReturn(List.of(muerta));
		when(taller.orden(10L))
				.thenReturn(Optional.of(orden(EstatusOrden.EN_PROCESO, EtapaTaller.DIAGNOSTICO, ENTREGA, List.of())));
		apns.responderCon("muerto", new ResultadoApns(410, "Unregistered"));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, MotivoCambio.ETAPA));

		assertThat(muerta.isActiva()).isFalse();
		verify(actividades).save(muerta);
	}

	@Test
	void unFalloQueNoEsDelTokenDejaLaActividadActiva() {
		ActividadEnVivo viva = actividad("tokenA");
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenReturn(List.of(viva));
		when(taller.orden(10L))
				.thenReturn(Optional.of(orden(EstatusOrden.EN_PROCESO, EtapaTaller.DIAGNOSTICO, ENTREGA, List.of())));
		apns.responderCon("tokenA", new ResultadoApns(503, "ServiceUnavailable"));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, MotivoCambio.ETAPA));

		assertThat(viva.isActiva()).isTrue();
		verify(actividades, never()).save(any());
	}

	@Test
	void losCambiosQueNoMuevenLaPantallaBloqueadaNoMandanNada() {
		for (MotivoCambio motivo : List.of(MotivoCambio.INSPECCION, MotivoCambio.RENGLON, MotivoCambio.REGISTRO)) {
			actualizador.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, motivo));
		}

		assertThat(apns.enviados()).isEmpty();
		verify(actividades, never()).findByOrdenServicioIdAndActivaTrue(any());
	}

	@Test
	void sinActividadesRegistradasNoLeeLaOrdenNiMandaNada() {
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenReturn(List.of());

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, MotivoCambio.ETAPA));

		assertThat(apns.enviados()).isEmpty();
		verify(taller, never()).orden(any());
	}

	@Test
	void sinCredencialesOSinIdentificadorDeLaAppNoHaceNada() {
		ActualizadorDeActividadesEnVivo sinBundle = nuevo(configuracion(""));
		sinBundle.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, MotivoCambio.ETAPA));
		apns.disponible(false);
		actualizador.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, MotivoCambio.ETAPA));

		assertThat(apns.enviados()).isEmpty();
		verify(actividades, never()).findByOrdenServicioIdAndActivaTrue(any());
	}

	@Test
	void unaExcepcionNuncaRompeLaTransaccionDeLaOrden() {
		when(actividades.findByOrdenServicioIdAndActivaTrue(10L)).thenThrow(new IllegalStateException("base caída"));

		actualizador.alCambiarLaOrden(cambio(EtapaTaller.DIAGNOSTICO, MotivoCambio.ETAPA));

		assertThat(apns.enviados()).isEmpty();
	}
}
