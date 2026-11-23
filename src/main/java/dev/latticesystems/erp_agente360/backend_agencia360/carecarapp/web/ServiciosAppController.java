package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ServicioApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ServicioDetalleApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ServiciosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de servicios y tickets de los vehículos del cliente (APP-3.2),
 * según el contrato en {@code docs/api/agencia360-api-app-v1.yaml}.
 */
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App CareCar - Servicios y tickets", description = "Historial de servicios del vehículo y sus tickets")
public class ServiciosAppController {

	private final ServiciosAppService serviciosAppService;

	public ServiciosAppController(ServiciosAppService serviciosAppService) {
		this.serviciosAppService = serviciosAppService;
	}

	@GetMapping("/vehiculos/{vehiculoId}/servicios")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Historial de servicios del vehículo", description = "El más reciente primero. Paginado "
			+ "con pagina (desde 0) y tamano (1 a 50, 20 por omisión); anio filtra por el año del servicio. "
			+ "Responde 404 si el vehículo no es del cliente.")
	public Pagina<ServicioApp> servicios(@PathVariable Long vehiculoId, @RequestParam(required = false) Integer anio,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return serviciosAppService.servicios(cliente.id(), vehiculoId, anio, pagina, tamano);
	}

	@GetMapping("/vehiculos/{vehiculoId}/servicios/anios")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Años con servicios del vehículo", description = "Del más reciente al más antiguo, para el "
			+ "filtro del historial. Responde 404 si el vehículo no es del cliente.")
	public List<Integer> anios(@PathVariable Long vehiculoId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return serviciosAppService.anios(cliente.id(), vehiculoId);
	}

	@GetMapping("/servicios/{servicioId}")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Ticket de una orden de servicio", description = "Mano de obra y refacciones con cantidad, "
			+ "precio e importe, y los totales. Responde 404 si el servicio no existe o es de un vehículo ajeno.")
	public ServicioDetalleApp detalle(@PathVariable Long servicioId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return serviciosAppService.detalle(cliente.id(), servicioId);
	}

	@GetMapping(path = "/servicios/{servicioId}/ticket", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Descargar el ticket de una orden de servicio", description = "PDF en el cuerpo. "
			+ "Responde 404 si el servicio no existe o es de un vehículo ajeno.")
	public ResponseEntity<byte[]> ticket(@PathVariable Long servicioId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		byte[] pdf = serviciosAppService.ticket(cliente.id(), servicioId);
		ContentDisposition disposicion = ContentDisposition.attachment().filename("ticket-" + servicioId + ".pdf")
				.build();
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString()).body(pdf);
	}
}
