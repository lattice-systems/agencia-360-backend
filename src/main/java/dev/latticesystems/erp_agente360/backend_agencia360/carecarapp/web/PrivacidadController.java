package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PrivacidadService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aviso de privacidad y su aceptación desde la app (M22.01). */
@RestController
@RequestMapping("/api/v1/app/privacidad")
@Tag(name = "App CareCar · Privacidad")
public class PrivacidadController {

	private final PrivacidadService privacidad;

	public PrivacidadController(PrivacidadService privacidad) {
		this.privacidad = privacidad;
	}

	/** El aviso que el cliente leyó y acepta. */
	public record AceptarAvisoRequest(@NotNull Long avisoId) {
	}

	@GetMapping("/aviso")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "El aviso de privacidad vigente", description = "V2-M22.01 · Con `aceptado` según esta cuenta: "
			+ "falso en el primer ingreso y cada vez que hay una versión nueva. 404 si no hay ninguno publicado.")
	public PrivacidadService.AvisoApp vigente(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return privacidad.vigente(cliente.id());
	}

	@PostMapping("/aceptaciones")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Aceptar el aviso de privacidad", description = "V2-M22.01 · Idempotente: repetirlo devuelve "
			+ "la primera aceptación. 404 aviso inexistente; 409 AVISO_NO_VIGENTE si hay una versión más reciente.")
	public PrivacidadService.AceptacionApp aceptar(@Valid @RequestBody AceptarAvisoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return privacidad.aceptar(cliente.id(), request.avisoId());
	}
}
