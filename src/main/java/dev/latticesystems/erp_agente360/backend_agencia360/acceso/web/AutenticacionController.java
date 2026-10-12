package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.application.AutenticacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Acceso", description = "Inicio de sesión del personal en el ERP y el CRM")
public class AutenticacionController {

	private final AutenticacionService autenticacionService;

	public AutenticacionController(AutenticacionService autenticacionService) {
		this.autenticacionService = autenticacionService;
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Iniciar sesión", description = "Responde 401 si las credenciales son incorrectas y 403 si el "
			+ "rol del usuario no tiene acceso a la aplicación pedida.")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return LoginResponse
				.de(autenticacionService.iniciarSesion(request.usuario(), request.contrasena(), request.aplicacion()));
	}
}
