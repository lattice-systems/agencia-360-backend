package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AyudaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PreguntaFrecuenteApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API móvil de preguntas frecuentes y reportes (V2-M27.01 y .04). */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
public class AyudaAppController {

	private final AyudaAppService ayuda;

	public AyudaAppController(AyudaAppService ayuda) {
		this.ayuda = ayuda;
	}

	@GetMapping("/ayuda/preguntas-frecuentes")
	public List<PreguntaFrecuenteApp> preguntasFrecuentes() {
		return ayuda.preguntasFrecuentes();
	}

	@PostMapping("/ayuda/reportes")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reportar(@Valid @RequestBody ReporteAppRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		ayuda.reportar(cliente.id(), request.descripcion(), request.versionApp(), request.sistema(),
				request.dispositivo());
	}
}
