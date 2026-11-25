package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@Component
class EventosEnVivoApp {

	static final String EVENTO_ORDEN = "orden";

	private static final Duration DURACION = Duration.ofMinutes(30);
	private static final Duration REINTENTO = Duration.ofSeconds(5);

	private final Map<Long, Set<SseEmitter>> conexiones = new ConcurrentHashMap<>();

	SseEmitter suscribir(Long clienteId) {
		SseEmitter conexion = new SseEmitter(DURACION.toMillis());
		conexion.onCompletion(() -> quitar(clienteId, conexion));
		conexion.onTimeout(conexion::complete);
		conexion.onError(error -> quitar(clienteId, conexion));
		if (enviar(clienteId, conexion, SseEmitter.event().reconnectTime(REINTENTO.toMillis()).comment("conectado"))) {
			conexiones.computeIfAbsent(clienteId, id -> ConcurrentHashMap.newKeySet()).add(conexion);
		}
		return conexion;
	}

	@TransactionalEventListener(fallbackExecution = true)
	void alCambiarOrden(OrdenServicioCambiada cambio) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("ordenId", cambio.ordenId());
		datos.put("vehiculoId", cambio.vehiculoId());
		datos.put("estatus", cambio.estatus().name());
		datos.put("etapa", cambio.etapa() == null ? null : cambio.etapa().name());
		datos.put("motivo", cambio.motivo() == null ? null : cambio.motivo().name());
		conexiones.getOrDefault(cambio.clienteId(), Set.of()).forEach(conexion -> enviar(cambio.clienteId(), conexion,
				SseEmitter.event().name(EVENTO_ORDEN).data(datos, MediaType.APPLICATION_JSON)));
	}

	/**
	 * Manda cualquier aviso del módulo (asistencia, traslados) solo a las
	 * conexiones del cliente dueño, con el nombre y los datos que trae.
	 */
	@TransactionalEventListener(fallbackExecution = true)
	void alAvisarAlCliente(AvisoAlCliente aviso) {
		conexiones.getOrDefault(aviso.clienteId(), Set.of()).forEach(conexion -> enviar(aviso.clienteId(), conexion,
				SseEmitter.event().name(aviso.evento()).data(aviso.datos(), MediaType.APPLICATION_JSON)));
	}

	@Scheduled(fixedRate = 25_000)
	void latido() {
		conexiones.forEach((clienteId, delCliente) -> delCliente
				.forEach(conexion -> enviar(clienteId, conexion, SseEmitter.event().comment("latido"))));
	}

	@EventListener(ContextClosedEvent.class)
	void alApagar() {
		conexiones.values().forEach(delCliente -> delCliente.forEach(SseEmitter::complete));
		conexiones.clear();
	}

	int conexionesDe(Long clienteId) {
		return conexiones.getOrDefault(clienteId, Set.of()).size();
	}

	private boolean enviar(Long clienteId, SseEmitter conexion, SseEventBuilder evento) {
		try {
			conexion.send(evento);
			return true;
		} catch (IOException | IllegalStateException cerrada) {
			quitar(clienteId, conexion);
			return false;
		}
	}

	private void quitar(Long clienteId, SseEmitter conexion) {
		conexiones.computeIfPresent(clienteId, (id, delCliente) -> {
			delCliente.remove(conexion);
			return delCliente.isEmpty() ? null : delCliente;
		});
	}
}
