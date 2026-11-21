package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.NotificacionesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tokens de notificaciones push de los teléfonos del cliente (APP-6). */
@RestController
@RequestMapping("/api/v1/app/dispositivos")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Notificaciones", description = "Registro del token de FCM del teléfono")
public class DispositivosController {

	private final NotificacionesService notificaciones;

	public DispositivosController(NotificacionesService notificaciones) {
		this.notificaciones = notificaciones;
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Registrar el token del teléfono", description = "Después de iniciar sesión y cada vez que "
			+ "Firebase cambie el token. Idempotente; si el token era de otro cliente, pasa a este.")
	public void registrar(@Valid @RequestBody DispositivoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		notificaciones.registrar(cliente.id(), request.token(), request.plataforma());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Borrar el token del teléfono", description = "Antes de cerrar sesión. Responde 204 aunque "
			+ "el token no exista.")
	public void borrar(@Valid @RequestBody BajaDispositivoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		notificaciones.borrar(cliente.id(), request.token());
	}
}
