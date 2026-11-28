package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EncuestasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EncuestasAppService.Agregado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EncuestasAppService.Tarea;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EncuestasAgenciaController {

	private final EncuestasAppService encuestas;

	public EncuestasAgenciaController(EncuestasAppService encuestas) {
		this.encuestas = encuestas;
	}

	@GetMapping("/api/v1/analitica/nps")
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE_MARKETING')")
	public List<Agregado> agregados(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return encuestas.agregados(desde, hasta);
	}

	@GetMapping("/api/v1/servicio/encuestas/contactos")
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SERVICIO')")
	public List<Tarea> tareas(@AuthenticationPrincipal UsuarioAutenticado personal) {
		return encuestas.tareas(personal.id(), personal.tieneRol(Role.ADMINISTRADOR));
	}

	@PostMapping("/api/v1/servicio/encuestas/contactos/{tareaId}/respuesta")
	@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SERVICIO')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void responder(@PathVariable Long tareaId, @AuthenticationPrincipal UsuarioAutenticado personal,
			@Valid @RequestBody Respuesta respuesta) {
		encuestas.registrarRespuesta(tareaId, personal.id(), personal.tieneRol(Role.ADMINISTRADOR), respuesta.texto());
	}

	public record Respuesta(@NotBlank @Size(max = 1000) String texto) {
	}
}
