package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ManualApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ManualesService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.VehiculoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.VehiculosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Vehículos del cliente y sus manuales en la app (APP-3.1), según el contrato
 * en {@code docs/api/agencia360-api-app-v1.yaml}.
 */
@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App CareCar - Vehículos", description = "Vehículos del cliente y sus manuales")
public class VehiculosAppController {

	static final String RUTA_MANUALES = "/api/v1/app/manuales";

	private final VehiculosAppService vehiculosAppService;
	private final ManualesService manualesService;

	public VehiculosAppController(VehiculosAppService vehiculosAppService, ManualesService manualesService) {
		this.vehiculosAppService = vehiculosAppService;
		this.manualesService = manualesService;
	}

	@GetMapping("/vehiculos")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Listar los vehículos del cliente", description = "En el orden en que se le asignaron.")
	public List<VehiculoApp> vehiculos(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return vehiculosAppService.listar(cliente.id());
	}

	@GetMapping("/vehiculos/{vehiculoId}/manuales")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Manuales de la marca, tipo y modelo del vehículo", description = "Cada uno con una URL "
			+ "temporal firmada para descargar el PDF sin el Bearer; vence a los 15 minutos. Responde 404 si el "
			+ "vehículo no existe o no es del cliente.")
	public List<ManualApp> manuales(@PathVariable Long vehiculoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		String urlBase = ServletUriComponentsBuilder.fromCurrentContextPath().path(RUTA_MANUALES).toUriString();
		return vehiculosAppService.manuales(cliente.id(), vehiculoId, urlBase);
	}

	@GetMapping(path = "/manuales/{manualId}/archivo", produces = MediaType.APPLICATION_PDF_VALUE)
	@SecurityRequirements
	@Operation(summary = "Descargar el PDF de un manual con la URL temporal", description = "No pide el Bearer: "
			+ "la URL trae su vencimiento y su firma. Responde 404 si la firma no es válida o ya venció.")
	public ResponseEntity<byte[]> archivo(@PathVariable Long manualId, @RequestParam(required = false) String expira,
			@RequestParam(required = false) String firma) {
		long vence;
		try {
			vence = Long.parseLong(expira);
		} catch (NumberFormatException sinVencimiento) {
			throw new RecursoNoEncontradoException(
					"El enlace del manual no es válido o ya venció; vuelve a pedir la lista de manuales");
		}
		ManualesService.Archivo archivo = manualesService.descargar(manualId, vence, firma);
		ContentDisposition disposicion = ContentDisposition.inline().filename(archivo.nombre(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString()).body(archivo.contenido());
	}
}
