package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inventario en tiempo real (ERP-2.1): quien tiene permiso de ver los vehículos
 * recibe un aviso por Server-Sent Events cada vez que cambia el inventario. Usa
 * un servidor real porque el aviso viaja por una conexión abierta y se manda
 * después del commit, así que esta prueba confirma sus transacciones y borra
 * sus datos al terminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VehiculosEnVivoIntegrationTest extends AbstractIntegrationTest {

	private static final Duration ESPERA = Duration.ofSeconds(10);

	@LocalServerPort
	private int puerto;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(ESPERA).build();

	private final List<Usuario> usuarios = new ArrayList<>();

	private final List<InputStream> conexiones = new ArrayList<>();

	@AfterEach
	void limpiar() throws IOException {
		for (InputStream conexion : conexiones) {
			conexion.close();
		}
		for (Usuario usuario : usuarios) {
			jdbcTemplate.update("delete from movimiento_inventario where responsable_id = ?", usuario.getId());
		}
		jdbcTemplate.update("delete from vehiculo where vin like '3VWFE21C04M10000%'");
		for (Usuario usuario : usuarios) {
			jdbcTemplate.update("delete from usuario where id = ?", usuario.getId());
		}
	}

	@Test
	void avisaALosSuscritosCuandoSeDaDeAltaUnaUnidad() throws Exception {
		String inventario = token(registrar("vivo.inventario", Role.INVENTARIO));
		BlockingQueue<String> avisos = suscribir(token(registrar("vivo.admin", Role.ADMINISTRADOR)));

		HttpResponse<String> alta = enviar("POST", "/api/v1/vehiculos", inventario, cuerpo("3VWFE21C04M100001"));

		assertThat(alta.statusCode()).isEqualTo(201);
		assertThat(siguienteEvento(avisos)).isEqualTo("vehiculos");
	}

	@Test
	void avisaCuandoSeCorrigeUnaUnidad() throws Exception {
		String inventario = token(registrar("vivo.inventario", Role.INVENTARIO));
		HttpResponse<String> alta = enviar("POST", "/api/v1/vehiculos", inventario, cuerpo("3VWFE21C04M100002"));
		String id = alta.body().replaceAll(".*\"id\":(\\d+).*", "$1");
		BlockingQueue<String> avisos = suscribir(inventario);

		HttpResponse<String> edicion = enviar("PUT", "/api/v1/vehiculos/" + id, inventario,
				cuerpo("3VWFE21C04M100002").replace("Blanco", "Rojo"));

		assertThat(edicion.statusCode()).isEqualTo(200);
		assertThat(siguienteEvento(avisos)).isEqualTo("vehiculos");
	}

	@Test
	void unaAltaRechazadaNoAvisa() throws Exception {
		String inventario = token(registrar("vivo.inventario", Role.INVENTARIO));
		enviar("POST", "/api/v1/vehiculos", inventario, cuerpo("3VWFE21C04M100003"));
		BlockingQueue<String> avisos = suscribir(inventario);

		HttpResponse<String> repetida = enviar("POST", "/api/v1/vehiculos", inventario, cuerpo("3VWFE21C04M100003"));

		assertThat(repetida.statusCode()).isEqualTo(409);
		assertThat(avisos.poll(1, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void ventasTambienSeSuscribePorqueVeLasDisponibles() throws Exception {
		HttpResponse<InputStream> respuesta = abrir(token(registrar("vivo.ventas", Role.VENTAS)));

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.headers().firstValue("Content-Type"))
				.hasValueSatisfying(tipo -> assertThat(tipo).startsWith("text/event-stream"));
	}

	@Test
	void unRolSinPermisoDeVerVehiculosNoSeSuscribe() throws Exception {
		HttpResponse<InputStream> respuesta = abrir(token(registrar("vivo.servicio", Role.SERVICIO)));

		assertThat(respuesta.statusCode()).isEqualTo(403);
	}

	@Test
	void sinSesionNoSeSuscribe() throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(url("/api/v1/vehiculos/eventos")).timeout(ESPERA).GET().build();

		assertThat(http.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
	}

	/**
	 * Abre la conexión y lee los nombres de evento en otro hilo. Espera el
	 * comentario inicial para no perder el aviso que llegue justo después.
	 */
	private BlockingQueue<String> suscribir(String token) throws Exception {
		HttpResponse<InputStream> respuesta = abrir(token);
		assertThat(respuesta.statusCode()).isEqualTo(200);
		BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
		BlockingQueue<Boolean> conectado = new LinkedBlockingQueue<>();
		Thread lector = new Thread(() -> {
			try (BufferedReader lineas = new BufferedReader(
					new InputStreamReader(respuesta.body(), StandardCharsets.UTF_8))) {
				String linea;
				while ((linea = lineas.readLine()) != null) {
					if (linea.startsWith(":")) {
						conectado.offer(true);
					} else if (linea.startsWith("event:")) {
						eventos.offer(linea.substring("event:".length()).trim());
					}
				}
			} catch (IOException cerrada) {
				// La prueba cerró la conexión al terminar.
			}
		});
		lector.setDaemon(true);
		lector.start();
		assertThat(conectado.poll(ESPERA.toSeconds(), TimeUnit.SECONDS)).isTrue();
		return eventos;
	}

	private HttpResponse<InputStream> abrir(String token) throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(url("/api/v1/vehiculos/eventos"))
				.header("Authorization", "Bearer " + token).header("Accept", "text/event-stream, application/json")
				.GET().build();
		HttpResponse<InputStream> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
		conexiones.add(respuesta.body());
		return respuesta;
	}

	private static String siguienteEvento(BlockingQueue<String> eventos) throws InterruptedException {
		return eventos.poll(ESPERA.toSeconds(), TimeUnit.SECONDS);
	}

	private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(url(ruta)).timeout(ESPERA)
				.header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
				.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo)).build();
		return http.send(peticion, HttpResponse.BodyHandlers.ofString());
	}

	private URI url(String ruta) {
		return URI.create("http://localhost:" + puerto + ruta);
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		Usuario guardado = usuarioRepository
				.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
		usuarios.add(guardado);
		return guardado;
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private static String cuerpo(String vin) {
		return """
				{"vin":"%s","modelo":"Jetta","version":"Comfortline","anio":2026,"color":"Blanco","precio":389900.00}
				""".formatted(vin);
	}
}
