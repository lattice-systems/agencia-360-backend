package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.web;

import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.DispositivosService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Token de push de cada instalación de la app (V2-M06.02). */
@RestController
@RequestMapping("/api/v1/app/dispositivos")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Notificaciones", description = "Token de push del dispositivo")
public class DispositivosAppController {

	private final DispositivosService dispositivos;

	public DispositivosAppController(DispositivosService dispositivos) {
		this.dispositivos = dispositivos;
	}

	@PutMapping("/{instalacionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Registrar o actualizar el token de push de esta instalación", description = "La app lo "
			+ "llama tras iniciar sesión y cuando Firebase le da un token nuevo. Es idempotente: la instalación "
			+ "queda a nombre de quien llama y el mismo token en otra instalación se descarta.")
	public void registrar(@PathVariable String instalacionId, @Valid @RequestBody RegistroDispositivoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		dispositivos.registrar(cliente.id(), instalacionId, request.tokenFcm(), request.plataforma(), request.idioma());
	}

	@DeleteMapping("/{instalacionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Dar de baja el token de esta instalación", description = "La app lo llama antes de "
			+ "cerrar sesión, porque /auth/cerrar-sesion ya no lleva access token. Idempotente: una instalación "
			+ "que no existe o que es de otra cuenta responde 204 y no se toca.")
	public void darDeBaja(@PathVariable String instalacionId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		dispositivos.darDeBaja(cliente.id(), instalacionId);
	}
}
