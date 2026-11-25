package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PrivacidadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publicación de versiones nuevas del aviso de privacidad por el Gerente de
 * marketing (M22.01). Un aviso publicado no se edita ni se borra.
 */
@RestController
@RequestMapping("/api/v1/avisos-privacidad")
@PreAuthorize("hasRole('GERENTE_MARKETING')")
@Tag(name = "Avisos de privacidad", description = "Publicar una versión nueva del aviso de privacidad")
public class AvisosPrivacidadController {

	private final PrivacidadService privacidad;

	public AvisosPrivacidadController(PrivacidadService privacidad) {
		this.privacidad = privacidad;
	}

	/** El texto y, si se quiere programar, desde cuándo rige. */
	public record PublicarAvisoRequest(String texto, Instant vigenteDesde) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Publicar una versión nueva del aviso", description = "V2-M22.01 · Es la versión mayor + 1. "
			+ "Sin `vigenteDesde` rige desde ahora; con él se programa, pero no puede ser anterior a ahora ni a la "
			+ "versión previa. Cada cliente tendrá que aceptarla de nuevo. 400 con el texto vacío o de más de "
			+ "20 000 caracteres.")
	public PrivacidadService.AvisoApp publicar(@RequestBody PublicarAvisoRequest request) {
		return privacidad.publicar(request.texto(), request.vigenteDesde());
	}
}
