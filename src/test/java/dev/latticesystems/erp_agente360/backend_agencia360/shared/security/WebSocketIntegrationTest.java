package dev.latticesystems.erp_agente360.backend_agencia360.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest extends AbstractIntegrationTest {

	private static final long ESPERA_SEGUNDOS = 10;

	@LocalServerPort
	private int puerto;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private SimpMessagingTemplate mensajes;

	private final WebSocketStompClient cliente = clienteStomp();

	@AfterEach
	void cerrar() {
		cliente.stop();
	}

	@Test
	void conTokenRecibeLosAvisosDeSuUsuario() throws Exception {
		StompSession sesion = conectar(conToken("ana.socket@correo.com"));
		BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
		sesion.subscribe("/user/queue/avisos", recibirEn(avisos));

		String recibido = null;
		for (int intento = 0; intento < 50 && recibido == null; intento++) {
			mensajes.convertAndSendToUser("ana.socket@correo.com", "/queue/avisos", "hola");
			recibido = avisos.poll(200, TimeUnit.MILLISECONDS);
		}

		assertThat(recibido).isEqualTo("hola");
	}

	@Test
	void noRecibeLosAvisosDeOtroUsuario() throws Exception {
		StompSession sesion = conectar(conToken("ana.socket@correo.com"));
		BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
		sesion.subscribe("/user/queue/avisos", recibirEn(avisos));

		for (int intento = 0; intento < 5; intento++) {
			mensajes.convertAndSendToUser("bruno.socket@correo.com", "/queue/avisos", "no es para Ana");
		}

		assertThat(avisos.poll(1, TimeUnit.SECONDS)).isNull();
	}

	@Test
	void sinTokenNoSeConecta() {
		assertThatThrownBy(() -> conectar(new StompHeaders())).isInstanceOfAny(ExecutionException.class,
				TimeoutException.class);
	}

	@Test
	void conUnTokenFalsoNoSeConecta() {
		StompHeaders encabezados = new StompHeaders();
		encabezados.add(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-token");

		assertThatThrownBy(() -> conectar(encabezados)).isInstanceOfAny(ExecutionException.class,
				TimeoutException.class);
	}

	@Test
	void noSePuedeSuscribirFueraDeSusAvisos() throws Exception {
		StompSession sesion = conectar(conToken("ana.socket@correo.com"));

		sesion.subscribe("/queue/avisos", recibirEn(new LinkedBlockingQueue<>()));

		for (int intento = 0; intento < 50 && sesion.isConnected(); intento++) {
			Thread.sleep(100);
		}
		assertThat(sesion.isConnected()).isFalse();
	}

	private StompHeaders conToken(String usuario) {
		StompHeaders encabezados = new StompHeaders();
		encabezados.add(HttpHeaders.AUTHORIZATION,
				"Bearer " + jwtService.generateToken(42L, usuario, List.of(Role.CLIENTE_CARECAR)));
		return encabezados;
	}

	private StompSession conectar(StompHeaders encabezados) throws Exception {
		WebSocketHttpHeaders apertura = new WebSocketHttpHeaders();
		apertura.setOrigin("http://localhost:5173");
		return cliente.connectAsync("ws://localhost:" + puerto + "/ws", apertura, encabezados,
				new StompSessionHandlerAdapter() {
				}).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
	}

	private static StompFrameHandler recibirEn(BlockingQueue<String> avisos) {
		return new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders encabezados) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders encabezados, Object cuerpo) {
				avisos.offer((String) cuerpo);
			}
		};
	}

	private static WebSocketStompClient clienteStomp() {
		WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
		stomp.setMessageConverter(new StringMessageConverter());
		stomp.setDefaultHeartbeat(new long[]{0, 0});
		return stomp;
	}
}
