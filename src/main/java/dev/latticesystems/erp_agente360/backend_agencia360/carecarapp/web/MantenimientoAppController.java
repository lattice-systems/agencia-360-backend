package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LecturaKmApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.MantenimientoAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.MantenimientoAppService.FotoLectura;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PlanMantenimientoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

/** Mantenimiento inteligente de la app (V2-M05.01, M05.02, M05.03 y M05.06). */
@RestController
@RequestMapping("/api/v1/app/vehiculos/{vehiculoId}")
@Tag(name = "App CareCar - Mantenimiento", description = "Plan de mantenimiento, próximo servicio y kilometraje")
public class MantenimientoAppController {

	private final MantenimientoAppService mantenimientoAppService;

	public MantenimientoAppController(MantenimientoAppService mantenimientoAppService) {
		this.mantenimientoAppService = mantenimientoAppService;
	}

	@GetMapping("/mantenimiento")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Plan de mantenimiento del vehículo", description = "Servicios del modelo con lo que "
			+ "incluyen, su precio estimado y su estado (hecho, pendiente, próximo o vencido), y cuándo toca el "
			+ "próximo. Responde 404 si el vehículo no es del cliente.")
	public PlanMantenimientoApp plan(@PathVariable Long vehiculoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return mantenimientoAppService.plan(cliente.id(), vehiculoId);
	}

	@PostMapping(path = "/lecturas-km", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Registrar mi kilometraje actual", description = "No sobrescribe el km oficial del taller. "
			+ "Responde 404 si el vehículo no es del cliente y 422 con KM_INVALIDO si el km es menor a la última "
			+ "lectura oficial o del cliente.")
	public LecturaKmApp registrarLectura(@PathVariable Long vehiculoId,
			@Valid @RequestBody NuevaLecturaKmRequest request, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return mantenimientoAppService.registrarLectura(cliente.id(), vehiculoId, request.km());
	}

	@PostMapping(path = "/lecturas-km", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Registrar mi kilometraje con una foto del tablero", description = "Igual que con JSON, "
			+ "en multipart/form-data: km y foto opcional (imagen JPEG, PNG o WebP). La respuesta trae fotoUrl, una "
			+ "URL firmada de 15 minutos. Responde 400 si el km no es válido o la foto no es una imagen, 404 si el "
			+ "vehículo no es del cliente y 422 con KM_INVALIDO si el km es menor al conocido.")
	public LecturaKmApp registrarLecturaConFoto(@PathVariable Long vehiculoId, @RequestParam Integer km,
			@RequestParam(required = false) MultipartFile foto, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		FotoLectura adjunta = null;
		if (foto != null && !foto.isEmpty()) {
			try {
				adjunta = new FotoLectura(foto.getContentType(), foto.getBytes());
			} catch (IOException ex) {
				throw new UncheckedIOException("No se pudo leer la foto", ex);
			}
		}
		return mantenimientoAppService.registrarLectura(cliente.id(), vehiculoId, km, adjunta);
	}
}
