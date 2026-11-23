package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AsistenciaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudAsistenciaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Asistencia en camino (SOS) de la app (V2-M11.01). */
@RestController
@RequestMapping("/api/v1/app/asistencia")
@Tag(name = "App CareCar - Asistencia", description = "Solicitudes de ayuda del cliente con su ubicación")
public class AsistenciaAppController {

	private final AsistenciaAppService asistenciaAppService;

	public AsistenciaAppController(AsistenciaAppService asistenciaAppService) {
		this.asistenciaAppService = asistenciaAppService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Pedir ayuda con mi ubicación", description = "Crea la solicitud en estado recibida. "
			+ "Responde 404 si el vehiculo no es del cliente y 409 con SOLICITUD_ABIERTA si ya tiene una abierta.")
	public SolicitudAsistenciaApp solicitar(@Valid @RequestBody NuevaSolicitudAsistenciaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return asistenciaAppService.solicitar(cliente.id(), request.vehiculoId(), request.tipo(), request.lat(),
				request.lng(), request.comentario());
	}

	@GetMapping
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Mi solicitud de asistencia abierta", description = "Con activa=true responde la "
			+ "solicitud recibida o en camino, o 204 si no hay ninguna.")
	public ResponseEntity<SolicitudAsistenciaApp> activa(@RequestParam boolean activa,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		if (!activa) {
			throw new IllegalArgumentException("Solo se admite activa=true");
		}
		return asistenciaAppService.activa(cliente.id()).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
