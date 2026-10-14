package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.ProspectoRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.RegistroProspectoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Registro de prospectos (CRM-1.0). Solo lo hace Ventas. */
@RestController
@RequestMapping("/api/v1/prospectos")
@Tag(name = "Prospectos", description = "Registro y seguimiento de prospectos del CRM")
public class RegistroProspectoController {

	private final RegistroProspectoService registroProspectoService;

	public RegistroProspectoController(RegistroProspectoService registroProspectoService) {
		this.registroProspectoService = registroProspectoService;
	}

	@PostMapping
	@PreAuthorize("hasRole('VENTAS')")
	@Operation(summary = "Registrar un prospecto", description = "Lo crea en Primer contacto, a cargo del vendedor "
			+ "en sesión. Responde 400 si falta el nombre, si faltan a la vez el teléfono y el correo, o si el "
			+ "teléfono no es de 10 dígitos. Si el teléfono o el correo ya existen lo registra de todos modos y "
			+ "responde contactoRepetido = true.")
	public ResponseEntity<ProspectoRegistrado> registrar(@Valid @RequestBody RegistrarProspectoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		ProspectoRegistrado creado = registroProspectoService.registrar(request.comoNuevoProspecto(), actor);
		return ResponseEntity.created(URI.create("/api/v1/prospectos/" + creado.prospecto().id())).body(creado);
	}

	@GetMapping("/coincidencias")
	@PreAuthorize("hasRole('VENTAS')")
	@Operation(summary = "Revisar si un teléfono o correo ya existen", description = "Para avisar mientras se "
			+ "captura. Busca en todos los prospectos y no dice de quién es el repetido.")
	public Coincidencias coincidencias(@RequestParam(required = false) String telefono,
			@RequestParam(required = false) String correo) {
		return new Coincidencias(registroProspectoService.hayContactoRepetido(telefono, correo));
	}

	/**
	 * @param contactoRepetido
	 *            el teléfono o el correo ya existen en otro prospecto
	 */
	public record Coincidencias(boolean contactoRepetido) {
	}
}
