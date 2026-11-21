package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AgendaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CitaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DisponibilidadApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agenda de citas de servicio de la app (APP-3.3), según el contrato en
 * {@code docs/api/agencia360-api-app-v1.yaml}.
 */
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App CareCar - Agenda", description = "Horarios con cupo y citas de servicio del cliente")
public class AgendaAppController {

	private final AgendaAppService agendaAppService;

	public AgendaAppController(AgendaAppService agendaAppService) {
		this.agendaAppService = agendaAppService;
	}

	@GetMapping("/agenda/disponibilidad")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Horarios con cupo para agendar", description = "Solo franjas con cupo mayor a cero que "
			+ "aún no empiezan. El rango es de hasta 31 días.")
	public List<DisponibilidadApp> disponibilidad(@RequestParam LocalDate desde, @RequestParam LocalDate hasta) {
		return agendaAppService.disponibilidad(desde, hasta);
	}

	@GetMapping("/citas")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Listar las citas del cliente", description = "Por fecha y hora de inicio. Filtros "
			+ "opcionales: estado, desde, hasta. Paginado.")
	public Pagina<CitaApp> citas(@RequestParam(required = false) EstadoCita estado,
			@RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return agendaAppService.citas(cliente.id(), estado, desde, hasta, pagina, tamano);
	}

	@PostMapping("/citas")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Agendar una cita de servicio", description = "Descuenta cupo de la franja. Responde 409 "
			+ "con codigo FRANJA_SIN_CUPO si ya no hay lugar.")
	public CitaApp agendar(@Valid @RequestBody NuevaCitaRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return agendaAppService.agendar(cliente.id(), request.vehiculoId(), request.franjaId(), request.fecha());
	}

	@PostMapping("/citas/{citaId}/cancelar")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Cancelar una cita", description = "Hasta app.agenda.horas-para-cancelar horas antes "
			+ "(24 por omisión; el valor final está pendiente del PO). Responde 409 con FUERA_DE_PLAZO o "
			+ "CITA_NO_CANCELABLE.")
	public CitaApp cancelar(@PathVariable Long citaId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return agendaAppService.cancelar(cliente.id(), citaId);
	}

	@GetMapping(path = "/citas/{citaId}/ticket", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Descargar el comprobante de una cita", description = "PDF en el cuerpo. "
			+ "Responde 404 si la cita no existe o es de otro cliente.")
	public org.springframework.http.ResponseEntity<byte[]> ticket(@PathVariable Long citaId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		byte[] pdf = agendaAppService.ticket(cliente.id(), citaId);
		return org.springframework.http.ResponseEntity.ok()
				.contentType(org.springframework.http.MediaType.APPLICATION_PDF)
				.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
						org.springframework.http.ContentDisposition.attachment()
								.filename("ticket-cita-" + citaId + ".pdf").build().toString())
				.body(pdf);
	}
}
