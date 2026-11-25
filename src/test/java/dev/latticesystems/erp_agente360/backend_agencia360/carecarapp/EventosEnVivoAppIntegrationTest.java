package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventosEnVivoAppIntegrationTest extends AbstractIntegrationTest {

	private static final Duration ESPERA = Duration.ofSeconds(10);

	@LocalServerPort
	private int puerto;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private TransactionTemplate transacciones;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(ESPERA).build();

	private final List<Long> clientes = new ArrayList<>();

	private final List<InputStream> conexiones = new ArrayList<>();

	@AfterEach
	void limpiar() throws IOException {
		for (InputStream conexion : conexiones) {
			conexion.close();
		}
		for (Long clienteId : clientes) {
			jdbcTemplate.update("delete from cuenta_app where cliente_id = ?", clienteId);
			jdbcTemplate.update("delete from cliente where id = ?", clienteId);
		}
	}

	@Test
	void elClienteRecibeElAvisoDeSuOrden() throws Exception {
		Long ana = clienteConCuenta("Ana En Vivo", "ana.envivo@correo.com");
		BlockingQueue<String> avisos = suscribir(token(ana, "ana.envivo@correo.com"));

		publicar(new OrdenServicioCambiada(41L, 7L, ana, EstatusOrden.TERMINADO, EtapaTaller.LISTO_PARA_ENTREGA,
				MotivoCambio.ETAPA));

		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).isEqualTo("orden");
		String datos = avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS);
		assertThat(datos).contains("\"ordenId\":41").contains("\"vehiculoId\":7").contains("\"estatus\":\"TERMINADO\"")
				.contains("\"etapa\":\"LISTO_PARA_ENTREGA\"").contains("\"motivo\":\"ETAPA\"");
	}

	@Test
	void unClienteNoRecibeLasOrdenesDeOtro() throws Exception {
		Long ana = clienteConCuenta("Ana En Vivo", "ana.envivo@correo.com");
		Long bruno = clienteConCuenta("Bruno En Vivo", "bruno.envivo@correo.com");
		BlockingQueue<String> avisos = suscribir(token(ana, "ana.envivo@correo.com"));

		publicar(new OrdenServicioCambiada(42L, 8L, bruno, EstatusOrden.TERMINADO, null, MotivoCambio.REGISTRO));

		assertThat(avisos.poll(1, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void elClienteRecibeLosAvisosDeAsistenciaYTraslado() throws Exception {
		Long ana = clienteConCuenta("Ana En Vivo", "ana.envivo@correo.com");
		BlockingQueue<String> avisos = suscribir(token(ana, "ana.envivo@correo.com"));

		publicar(new AvisoAlCliente(ana, "asistencia.estado", Map.of("solicitudId", 12, "estado", "en_camino")));
		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).isEqualTo("asistencia.estado");
		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).contains("\"solicitudId\":12")
				.contains("\"estado\":\"en_camino\"");

		publicar(new AvisoAlCliente(ana, "traslado.estado",
				Map.of("trasladoId", 5, "citaId", 9, "tipo", "recoger", "estado", "en_camino")));
		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).isEqualTo("traslado.estado");
		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).contains("\"trasladoId\":5")
				.contains("\"citaId\":9").contains("\"tipo\":\"recoger\"");

		publicar(new AvisoAlCliente(ana, "traslado.ubicacion",
				Map.of("trasladoId", 5, "lat", 21.13, "lng", -101.69, "actualizadaEn", "2026-01-01T10:00:00Z")));
		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).isEqualTo("traslado.ubicacion");
		assertThat(avisos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).contains("\"lat\":21.13")
				.contains("\"actualizadaEn\":\"2026-01-01T10:00:00Z\"");
	}

	@Test
	void unClienteNoRecibeLosAvisosDeOtro() throws Exception {
		Long ana = clienteConCuenta("Ana En Vivo", "ana.envivo@correo.com");
		Long bruno = clienteConCuenta("Bruno En Vivo", "bruno.envivo@correo.com");
		BlockingQueue<String> avisos = suscribir(token(ana, "ana.envivo@correo.com"));

		publicar(new AvisoAlCliente(bruno, "asistencia.estado", Map.of("solicitudId", 13, "estado", "atendida")));

		assertThat(avisos.poll(1, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void sinSesionNoSeSuscribe() throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(url()).timeout(ESPERA).GET().build();

		assertThat(http.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
	}

	@Test
	void elPersonalNoSeSuscribeALosAvisosDeLaApp() throws Exception {
		String token = jwtService.generateToken(999_999L, "servicio-en-vivo", List.of(Role.SERVICIO));

		assertThat(abrir(token).statusCode()).isEqualTo(403);
	}

	private void publicar(Object cambio) {
		transacciones.executeWithoutResult(estado -> eventos.publishEvent(cambio));
	}

	private Long clienteConCuenta(String nombre, String correo) {
		Long clienteId = registroClientesService.registrar(new NuevoCliente(nombre, correo, null, null)).id();
		clientes.add(clienteId);
		cuentasAppService.abrir(clienteId, "Temporal123");
		return clienteId;
	}

	private String token(Long clienteId, String correo) {
		return jwtService.generateToken(clienteId, correo, List.of(Role.CLIENTE_CARECAR));
	}

	private BlockingQueue<String> suscribir(String token) throws Exception {
		HttpResponse<InputStream> respuesta = abrir(token);
		assertThat(respuesta.statusCode()).isEqualTo(200);
		BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
		BlockingQueue<Boolean> conectado = new LinkedBlockingQueue<>();
		Thread lector = new Thread(() -> {
			try (BufferedReader lineas = new BufferedReader(
					new InputStreamReader(respuesta.body(), StandardCharsets.UTF_8))) {
				String linea;
				while ((linea = lineas.readLine()) != null) {
					if (linea.startsWith(":")) {
						conectado.offer(true);
					} else if (linea.startsWith("event:")) {
						avisos.offer(linea.substring("event:".length()).trim());
					} else if (linea.startsWith("data:")) {
						avisos.offer(linea.substring("data:".length()).trim());
					}
				}
			} catch (IOException cerrada) {
				conectado.offer(false);
			}
		});
		lector.setDaemon(true);
		lector.start();
		assertThat(conectado.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).isTrue();
		return avisos;
	}

	private HttpResponse<InputStream> abrir(String token) throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(url()).header("Authorization", "Bearer " + token)
				.header("Accept", "text/event-stream, application/json").GET().build();
		HttpResponse<InputStream> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
		conexiones.add(respuesta.body());
		return respuesta;
	}

	private URI url() {
		return URI.create("http://localhost:" + puerto + "/api/v1/app/eventos");
	}
}
