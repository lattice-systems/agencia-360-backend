package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AccesoAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PerfilApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RecuperacionContrasenaService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SesionIniciadaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acceso de los clientes a la app CareCar (APP-2.1, APP-2.2 y APP-2.3), según
 * el contrato v1.1 en {@code docs/api/agencia360-api-app-v1.yaml}.
 */
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App CareCar - Acceso", description = "Sesión, contraseña y perfil del cliente en la app")
public class AccesoAppController {

	private final AccesoAppService accesoAppService;
	private final RecuperacionContrasenaService recuperacionService;

	public AccesoAppController(AccesoAppService accesoAppService, RecuperacionContrasenaService recuperacionService) {
		this.accesoAppService = accesoAppService;
		this.recuperacionService = recuperacionService;
	}

	@PostMapping("/auth/login")
	@SecurityRequirements
	@Operation(summary = "Iniciar sesión con el correo", description = "Responde 401 (CREDENCIALES_INVALIDAS) si el "
			+ "correo o la contraseña no son correctos, 429 (DEMASIADOS_INTENTOS, con Retry-After) tras 5 intentos "
			+ "fallidos seguidos y 403 (CUENTA_DESACTIVADA) si la contraseña es correcta pero la cuenta está "
			+ "desactivada.")
	public SesionIniciadaApp login(@Valid @RequestBody LoginAppRequest request) {
		return accesoAppService.iniciarSesion(request.correo(), request.contrasena());
	}

	@PostMapping("/auth/refrescar")
	@SecurityRequirements
	@Operation(summary = "Obtener tokens nuevos con el refresh token", description = "Cada refresh token se usa una "
			+ "vez. Responde 401 si es inválido, usado, revocado o vencido, y 403 si la cuenta está desactivada.")
	public SesionIniciadaApp refrescar(@Valid @RequestBody RefreshTokenRequest request) {
		return accesoAppService.refrescar(request.refreshToken());
	}

	@PostMapping("/auth/cerrar-sesion")
	@SecurityRequirements
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cerrar la sesión", description = "Revoca el refresh token. Responde 204 aunque ya no sea "
			+ "válido.")
	public void cerrarSesion(@Valid @RequestBody RefreshTokenRequest request) {
		accesoAppService.cerrarSesion(request.refreshToken());
	}

	@PostMapping("/auth/cambiar-contrasena")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cambiar la contraseña", description = "Obligatorio en el primer ingreso. La nueva debe "
			+ "tener de 8 a 72 caracteres, al menos una letra y un número, y ser distinta de la actual; si no, "
			+ "responde 400 (DATOS_INVALIDOS).")
	public void cambiarContrasena(@Valid @RequestBody CambiarContrasenaAppRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		accesoAppService.cambiarContrasena(cliente.id(), request.contrasenaActual(), request.contrasenaNueva());
	}

	@PostMapping("/auth/recuperar-contrasena")
	@SecurityRequirements
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Pedir un código de recuperación por correo", description = "Envía un código de 6 dígitos "
			+ "que vence en 15 minutos. Responde 202 aunque el correo no exista.")
	public void recuperarContrasena(@Valid @RequestBody RecuperarContrasenaRequest request) {
		recuperacionService.solicitar(request.correo());
	}

	@PostMapping("/auth/restablecer-contrasena")
	@SecurityRequirements
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Definir una contraseña nueva con el código", description = "Responde 400 (CODIGO_INVALIDO) "
			+ "si el código no es válido, ya se usó o venció, y 400 (DATOS_INVALIDOS) si la contraseña no cumple "
			+ "las reglas. Cierra las sesiones abiertas de la cuenta.")
	public void restablecerContrasena(@Valid @RequestBody RestablecerContrasenaRequest request) {
		recuperacionService.restablecer(request.correo(), request.codigo(), request.contrasenaNueva());
	}

	@GetMapping("/perfil")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Datos del cliente que inició sesión")
	public PerfilApp perfil(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return accesoAppService.perfil(cliente.id());
	}
}
