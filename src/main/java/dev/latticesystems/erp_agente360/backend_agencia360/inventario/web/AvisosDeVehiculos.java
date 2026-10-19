package dev.latticesystems.erp_agente360.backend_agencia360.inventario.web;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.VehiculoCambiado;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Inventario de vehículos en tiempo real (ERP-2.1) con Server-Sent Events. Cada
 * pantalla abierta mantiene una conexión y, cuando cambia una unidad, recibe el
 * evento {@code vehiculos} para volver a consultar la lista. El aviso no trae
 * datos: la consulta aplica los permisos de cada rol (Ventas solo ve las
 * disponibles), así que nadie se entera de una unidad que no puede ver.
 *
 * <p>
 * Las conexiones viven en la memoria de esta instancia. Si la API llega a
 * correr en varias instancias, el aviso tendrá que pasar entre ellas (por
 * ejemplo, con Redis).
 */
@Component
class AvisosDeVehiculos {

	static final String EVENTO = "vehiculos";

	/**
	 * La conexión se cierra sola cada cierto tiempo; el navegador se vuelve a
	 * conectar y así se revisa otra vez el token.
	 */
	private static final Duration DURACION = Duration.ofMinutes(30);

	private static final Duration REINTENTO = Duration.ofSeconds(5);

	private final Set<SseEmitter> conexiones = ConcurrentHashMap.newKeySet();

	SseEmitter suscribir() {
		SseEmitter conexion = new SseEmitter(DURACION.toMillis());
		conexion.onCompletion(() -> conexiones.remove(conexion));
		conexion.onTimeout(conexion::complete);
		conexion.onError(error -> conexiones.remove(conexion));
		// El comentario inicial manda los encabezados de inmediato: así el navegador
		// sabe que ya está conectado aunque no haya cambios.
		if (enviar(conexion, SseEmitter.event().reconnectTime(REINTENTO.toMillis()).comment("conectado"))) {
			conexiones.add(conexion);
		}
		return conexion;
	}

	@TransactionalEventListener
	void alCambiar(VehiculoCambiado cambio) {
		conexiones.forEach(conexion -> enviar(conexion, SseEmitter.event().name(EVENTO).data("cambio")));
	}

	/**
	 * Mantiene viva la conexión a través de proxies que cortan las conexiones
	 * inactivas y descarta las de pantallas que ya se cerraron.
	 */
	@Scheduled(fixedRate = 25_000)
	void latido() {
		conexiones.forEach(conexion -> enviar(conexion, SseEmitter.event().comment("latido")));
	}

	/**
	 * Al apagar la API se cierran las conexiones; si no, el apagado ordenado espera
	 * a que terminen (hasta 30 min).
	 */
	@EventListener(ContextClosedEvent.class)
	void alApagar() {
		conexiones.forEach(SseEmitter::complete);
		conexiones.clear();
	}

	private boolean enviar(SseEmitter conexion, SseEventBuilder evento) {
		try {
			conexion.send(evento);
			return true;
		} catch (IOException | IllegalStateException cerrada) {
			// El navegador ya se fue: se descarta su conexión.
			conexiones.remove(conexion);
			return false;
		}
	}
}
