package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.DispositivosService;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.MensajePush;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.ResultadoEnvio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V2-M06.07 y V2-M05.05: los eventos de dominio que ya publica el backend
 * llegan como push y a la bandeja, con la migración V60 y base real. No es
 * {@code @Transactional}: el aviso sale al confirmar, así que los datos tienen
 * que estar confirmados de verdad.
 */
class AvisosPushIntegrationTest extends AbstractIntegrationTest {

	private static final Duration ESPERA = Duration.ofSeconds(10);

	@TestConfiguration
	static class Configuracion {
		@Bean
		@Primary
		EnvioPushFake envioPushFake() {
			return new EnvioPushFake();
		}
	}

	@Autowired
	private EnvioPushFake envio;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private DispositivosService dispositivos;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private TransactionTemplate transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private final List<Long> clientes = new ArrayList<>();
	private Long ana;
	private String tokenDeAna;

	@BeforeEach
	void preparar() {
		envio.reiniciar();
		ana = clienteConCuenta("Ana Push");
		tokenDeAna = "token-ana-" + UUID.randomUUID();
		dispositivos.registrar(ana, UUID.randomUUID().toString(), tokenDeAna, "android", "es");
	}

	@AfterEach
	void limpiar() {
		for (Long clienteId : clientes) {
			jdbc.update("delete from notificacion where cliente_id = ?", clienteId);
			jdbc.update("delete from dispositivo_push where cuenta_app_id in "
					+ "(select id from cuenta_app where cliente_id = ?)", clienteId);
			jdbc.update("delete from cuenta_app where cliente_id = ?", clienteId);
			jdbc.update("delete from cliente where id = ?", clienteId);
		}
	}

	private Long clienteConCuenta(String nombre) {
		Long id = registroClientesService
				.registrar(new NuevoCliente(nombre, "push." + UUID.randomUUID() + "@correo.com", null, null)).id();
		cuentasAppService.abrir(id, "Temporal123");
		clientes.add(id);
		return id;
	}

	private void publicar(Object evento) {
		transacciones.executeWithoutResult(estado -> eventos.publishEvent(evento));
	}

	private List<Map<String, Object>> bandeja(Long clienteId) {
		return jdbc.queryForList("select * from notificacion where cliente_id = ? order by id", clienteId);
	}

	private void esperarQueNoSalgaNada() {
		await().during(Duration.ofMillis(600)).atMost(Duration.ofSeconds(3)).until(() -> envio.enviados().isEmpty());
	}

	@Test
	void elRecordatorioDeServicioLlegaComoPushConSuTextoYQuedaEnLaBandeja() {
		publicar(new AvisoAlCliente(ana, "mantenimiento.recordatorio", Map.of("vehiculoId", 7L, "servicioId", 3L,
				"nombre", "Servicio de 10,000 km", "faltanKm", 850, "faltanDias", 40, "motivo", "km")));

		await().atMost(ESPERA).until(() -> !envio.enviados().isEmpty());
		MensajePush push = envio.enviados().get(0);
		assertThat(push.token()).isEqualTo(tokenDeAna);
		assertThat(push.titulo()).isEqualTo("Se acerca tu próximo servicio");
		assertThat(push.cuerpo())
				.isEqualTo("Servicio de 10,000 km: faltan unos 850 km. Agenda tu cita cuando quieras.");
		assertThat(push.datos()).containsEntry("evento", "mantenimiento.recordatorio")
				.containsEntry("destino", "/mantenimiento/7").containsEntry("vehiculoId", "7");
		assertThat(bandeja(ana)).hasSize(1);
		assertThat(bandeja(ana).get(0)).containsEntry("categoria", "mantenimiento")
				.containsEntry("titulo", "Se acerca tu próximo servicio").containsEntry("destino", "/mantenimiento/7")
				.containsEntry("evento", "mantenimiento.recordatorio");
		assertThat(bandeja(ana).get(0).get("leida_en")).isNull();
		assertThat(bandeja(ana).get(0).get("datos").toString()).contains("\"vehiculoId\"").contains("7")
				.doesNotContain("Servicio de 10,000 km");
	}

	@Test
	void cuandoElAutoQuedaListoElClienteRecibeElPush() {
		publicar(new OrdenServicioCambiada(41L, 7L, ana, EstatusOrden.TERMINADO, EtapaTaller.LISTO_PARA_ENTREGA,
				MotivoCambio.ETAPA));

		await().atMost(ESPERA).until(() -> !envio.enviados().isEmpty());
		assertThat(envio.enviados().get(0).titulo()).isEqualTo("Tu auto está listo");
		assertThat(envio.enviados().get(0).datos()).containsEntry("destino", "/taller/41");
		assertThat(bandeja(ana)).extracting(fila -> fila.get("categoria")).containsExactly("taller");
	}

	@Test
	void elAvisoDeUnClienteNoLlegaAlDispositivoDeOtro() {
		Long bruno = clienteConCuenta("Bruno Push");
		String tokenDeBruno = "token-bruno-" + UUID.randomUUID();
		dispositivos.registrar(bruno, UUID.randomUUID().toString(), tokenDeBruno, "ios", "es");

		publicar(new AvisoAlCliente(bruno, "asistencia.estado", Map.of("solicitudId", 4L, "estado", "en_camino")));

		await().atMost(ESPERA).until(() -> !envio.enviados().isEmpty());
		assertThat(envio.enviados()).extracting(MensajePush::token).containsExactly(tokenDeBruno);
		assertThat(bandeja(ana)).isEmpty();
	}

	@Test
	void unFalloDeFcmNuncaRevierteLaTransaccionQueCausoElAviso() {
		envio.lanzarExcepcion(true);

		transacciones.executeWithoutResult(estado -> {
			jdbc.update("update cliente set nombre = 'Ana Cambiada' where id = ?", ana);
			eventos.publishEvent(
					new AvisoAlCliente(ana, "asistencia.estado", Map.of("solicitudId", 4L, "estado", "atendida")));
		});

		await().atMost(ESPERA).until(() -> !envio.enviados().isEmpty());
		assertThat(jdbc.queryForObject("select nombre from cliente where id = ?", String.class, ana))
				.isEqualTo("Ana Cambiada");
		assertThat(bandeja(ana)).hasSize(1);
	}

	@Test
	void unTokenQueFcmNoConoceDesactivaElDispositivo() {
		envio.responder(ResultadoEnvio.TOKEN_INVALIDO);

		publicar(new AvisoAlCliente(ana, "asistencia.estado", Map.of("solicitudId", 4L, "estado", "atendida")));

		await().atMost(ESPERA).until(() -> activos() == 0);
		envio.reiniciar();
		publicar(new AvisoAlCliente(ana, "asistencia.estado", Map.of("solicitudId", 4L, "estado", "cancelada")));
		esperarQueNoSalgaNada();
		assertThat(bandeja(ana)).hasSize(2);
	}

	@Test
	void sinDispositivosElAvisoSoloQuedaEnLaBandeja() {
		jdbc.update("delete from dispositivo_push where cuenta_app_id in "
				+ "(select id from cuenta_app where cliente_id = ?)", ana);

		publicar(new AvisoAlCliente(ana, "factura.timbrada", Map.of("facturaId", 1L, "pagoId", 5L)));

		esperarQueNoSalgaNada();
		assertThat(bandeja(ana)).hasSize(1);
		assertThat(bandeja(ana).get(0)).containsEntry("categoria", "documentos");
	}

	@Test
	void unEventoQueNoMereceAvisoNoGeneraNada() {
		publicar(new AvisoAlCliente(ana, "traslado.ubicacion", Map.of("trasladoId", 9L, "lat", 21.1, "lng", -101.6)));

		esperarQueNoSalgaNada();
		assertThat(bandeja(ana)).isEmpty();
	}

	@Test
	void siLaTransaccionSeRevierteNoSeAvisaDeAlgoQueNoPaso() {
		transacciones.executeWithoutResult(estado -> {
			eventos.publishEvent(
					new AvisoAlCliente(ana, "asistencia.estado", Map.of("solicitudId", 4L, "estado", "atendida")));
			estado.setRollbackOnly();
		});

		esperarQueNoSalgaNada();
		assertThat(bandeja(ana)).isEmpty();
	}

	private int activos() {
		return jdbc
				.queryForObject("select count(*) from dispositivo_push d join cuenta_app c on c.id = d.cuenta_app_id "
						+ "where c.cliente_id = ? and d.activo", Integer.class, ana);
	}
}
