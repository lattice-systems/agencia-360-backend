package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.Pagina;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ValuacionApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ValuacionService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TomaValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valuación formal de la app (M26), según el contrato en
 * {@code docs/api/agencia360-api-app-v2.yaml}. El cliente sale del token y lo
 * ajeno responde 404.
 */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Valuación", description = "Valuación formal y toma a cuenta")
public class ValuacionController {

	private final ValuacionService valuaciones;

	public ValuacionController(ValuacionService valuaciones) {
		this.valuaciones = valuaciones;
	}

	@PostMapping("/valuaciones")
	@Operation(summary = "Pedir una valuación formal desde una estimación", description = "201 con la valuación "
			+ "nueva, o 200 con la que ya está abierta para ese vehículo. 404 si la estimación no es del cliente y "
			+ "422 VALUACION_SIN_VEHICULO si se estimó sin un vehículo registrado.")
	public ResponseEntity<ValuacionApp> solicitar(@Valid @RequestBody NuevaValuacionRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		ValuacionService.Solicitud solicitud = valuaciones.solicitar(cliente.id(), request.estimacionTomaCuentaId());
		return ResponseEntity.status(solicitud.creada() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(solicitud.valuacion());
	}

	@GetMapping("/valuaciones")
	@Operation(summary = "Listar las valuaciones del cliente", description = "La más reciente primero. Paginado.")
	public Pagina<ValuacionApp> listar(@AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
		return valuaciones.delCliente(cliente.id(), pagina, tamano);
	}

	@GetMapping("/valuaciones/{valuacionId}")
	@Operation(summary = "Ver una valuación", description = "Con fotos, cita, oferta y asesor.")
	public ValuacionApp ver(@PathVariable Long valuacionId, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return valuaciones.ver(cliente.id(), valuacionId);
	}

	@PostMapping(path = "/valuaciones/{valuacionId}/fotos/{toma}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Subir o reemplazar una foto guiada", description = "JPEG o PNG de hasta 8 MB y 40 "
			+ "megapíxeles; se guarda sin EXIF. 409 VALUACION_CERRADA si ya no admite fotos.")
	public ValuacionApp subirFoto(@PathVariable Long valuacionId, @PathVariable TomaValuacion toma,
			@RequestParam(required = false) MultipartFile imagen, @AuthenticationPrincipal UsuarioAutenticado cliente)
			throws IOException {
		return valuaciones.subirFoto(cliente.id(), valuacionId, toma, imagen == null ? null : imagen.getBytes());
	}
}
