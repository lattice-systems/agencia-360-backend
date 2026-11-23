package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App CareCar - En vivo", description = "Avisos en tiempo real para la app")
public class EventosAppController {

	private final EventosEnVivoApp eventos;

	EventosAppController(EventosEnVivoApp eventos) {
		this.eventos = eventos;
	}

	@GetMapping(path = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Avisos en vivo del cliente (Server-Sent Events)", description = "Manda el evento \"orden\" "
			+ "con ordenId, vehiculoId y estatus cada vez que cambia una orden de servicio de sus vehículos; la app "
			+ "vuelve a consultar la orden. Envía un comentario cada 25 s y cierra la conexión a los 30 min para que "
			+ "la app se reconecte con su token.")
	public SseEmitter eventos(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return eventos.suscribir(cliente.id());
	}
}
