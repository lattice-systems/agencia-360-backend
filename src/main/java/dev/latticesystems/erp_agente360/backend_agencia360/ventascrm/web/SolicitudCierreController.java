package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.web;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.SolicitudCierreVista;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application.SolicitudesCierreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solicitudes de cierre: desde Pago en adelante el vendedor pide cerrar el
 * prospecto y el Administrador del ERP decide (decisión del PO del 12 de
 * octubre).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Solicitudes de cierre", description = "Cierre de prospectos que ya están en Pago o después")
public class SolicitudCierreController {

	private final SolicitudesCierreService solicitudesCierreService;

	public SolicitudCierreController(SolicitudesCierreService solicitudesCierreService) {
		this.solicitudesCierreService = solicitudesCierreService;
	}

	@PostMapping("/prospectos/{id}/solicitudes-cierre")
	@PreAuthorize("hasRole('VENTAS')")
	@Operation(summary = "Solicitar el cierre de un prospecto", description = "Solo el vendedor dueño, con el "
			+ "prospecto en Pago, Facturación o Entrega. Responde 400 si falta el motivo, 409 si la etapa no "
			+ "corresponde o ya hay una solicitud pendiente, y 404 si el prospecto es de otro vendedor.")
	public ResponseEntity<SolicitudCierreVista> solicitar(@PathVariable Long id,
			@Valid @RequestBody SolicitarCierreRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
		SolicitudCierreVista creada = solicitudesCierreService.solicitar(id, request.motivo(), actor);
		return ResponseEntity.created(URI.create("/api/v1/solicitudes-cierre/" + creada.id())).body(creada);
	}

	@GetMapping("/solicitudes-cierre")
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	@Operation(summary = "Solicitudes de cierre pendientes", description = "De la más antigua a la más reciente.")
	public List<SolicitudCierreVista> pendientes(@AuthenticationPrincipal UsuarioAutenticado actor) {
		return solicitudesCierreService.pendientes(actor);
	}

	@PostMapping("/solicitudes-cierre/{id}/aprobar")
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	@Operation(summary = "Aprobar una solicitud de cierre", description = "El prospecto pasa a Cerrado con el "
			+ "motivo de la solicitud. Responde 409 si ya fue resuelta.")
	public SolicitudCierreVista aprobar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return solicitudesCierreService.aprobar(id, actor);
	}

	@PostMapping("/solicitudes-cierre/{id}/rechazar")
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	@Operation(summary = "Rechazar una solicitud de cierre", description = "El prospecto sigue en su etapa. "
			+ "Responde 409 si ya fue resuelta.")
	public SolicitudCierreVista rechazar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
		return solicitudesCierreService.rechazar(id, actor);
	}

	/** El motivo es obligatorio; esa regla la valida el dominio. */
	public record SolicitarCierreRequest(@Size(max = 200) String motivo) {
	}
}
