package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AsistenteService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RespuestaAsistenteApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Asistente CareCar (V2-M24.03): preguntas sobre el manual del vehículo. */
@RestController
@RequestMapping("/api/v1/app/asistente")
@Tag(name = "App CareCar · Asistente")
public class AsistenteController {

	private final AsistenteService asistenteService;

	public AsistenteController(AsistenteService asistenteService) {
		this.asistenteService = asistenteService;
	}

	@PostMapping("/preguntas")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Preguntar al asistente sobre el manual del vehículo", description = "Responde con base "
			+ "en el manual del modelo y año del vehículo, citando páginas; encontrado=false si el manual no lo "
			+ "dice. 404 si el vehículo no es del cliente; 409 ASISTENTE_SIN_MANUAL; 429 ASISTENTE_LIMITE con "
			+ "Retry-After; 503 ASISTENTE_NO_DISPONIBLE.")
	public RespuestaAsistenteApp preguntar(@Valid @RequestBody PreguntaAsistenteRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return asistenteService.preguntar(cliente.id(), request.vehiculoId(), request.pregunta());
	}

	@PostMapping("/consultas/{consultaId}/calificacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Calificar una respuesta del asistente", description = "V2-M24.07 · Guarda si la "
			+ "respuesta fue útil (👍/👎); calificar otra vez la reemplaza. 404 si la consulta no existe o no es "
			+ "del cliente.")
	public void calificar(@PathVariable Long consultaId, @Valid @RequestBody CalificacionAsistenteRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		asistenteService.calificar(cliente.id(), consultaId, request.util());
	}
}
