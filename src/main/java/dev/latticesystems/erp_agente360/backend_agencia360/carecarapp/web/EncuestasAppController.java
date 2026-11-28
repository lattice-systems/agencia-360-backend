package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EncuestasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EncuestasAppService.Calificacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EncuestasAppService.Pendiente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/encuestas")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
public class EncuestasAppController {

	private final EncuestasAppService encuestas;

	public EncuestasAppController(EncuestasAppService encuestas) {
		this.encuestas = encuestas;
	}

	@GetMapping("/pendientes")
	public List<Pendiente> pendientes(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return encuestas.pendientes(cliente.id());
	}

	@PostMapping("/{ordenId}/posponer")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void posponer(@PathVariable Long ordenId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		encuestas.posponer(cliente.id(), ordenId);
	}

	@PostMapping("/{ordenId}/responder")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void responder(@PathVariable Long ordenId, @AuthenticationPrincipal UsuarioAutenticado cliente,
			@Valid @RequestBody Respuesta respuesta) {
		encuestas.responder(cliente.id(), ordenId, new Calificacion(respuesta.nps(), respuesta.tiempo(),
				respuesta.trato(), respuesta.limpieza(), respuesta.asesor(), respuesta.comentario()));
	}

	@PostMapping("/{ordenId}/contacto")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void contacto(@PathVariable Long ordenId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		encuestas.pedirContacto(cliente.id(), ordenId);
	}

	public record Respuesta(@NotNull @Min(0) @Max(10) Integer nps, @NotNull @Min(1) @Max(5) Integer tiempo,
			@NotNull @Min(1) @Max(5) Integer trato, @NotNull @Min(1) @Max(5) Integer limpieza,
			@NotNull @Min(1) @Max(5) Integer asesor, @Size(max = 1000) String comentario) {
	}
}
