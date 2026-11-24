package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ChecklistTrasladoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ChecklistTrasladoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ChecklistTrasladoService.ArchivoSubido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SeguimientoTrasladoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.TrasladosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Operación del chofer sobre los traslados (V2-M17.03 y M17.04). Usa el rol de
 * marketing mientras el ERP siga congelado.
 */
@RestController
@RequestMapping("/api/v1/traslados")
@Tag(name = "Traslados (personal)", description = "El chofer mueve el traslado y reporta su ubicación")
public class TrasladosPersonalController {

	private final TrasladosAppService trasladosAppService;
	private final ChecklistTrasladoService checklistService;

	public TrasladosPersonalController(TrasladosAppService trasladosAppService,
			ChecklistTrasladoService checklistService) {
		this.trasladosAppService = trasladosAppService;
		this.checklistService = checklistService;
	}

	@PatchMapping("/{id}/estado")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Cambiar el estado de un traslado", description = "Recoger: en_camino, recogido, en_taller. "
			+ "Entregar: de_regreso, entregado. Avisa al cliente (traslado.estado). Responde 400 si el estado no "
			+ "existe, 404 si el traslado no existe y 409 con TRANSICION_INVALIDA si no sigue al estado actual.")
	public SeguimientoTrasladoApp cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request,
			@AuthenticationPrincipal UsuarioAutenticado chofer) {
		return trasladosAppService.cambiarEstado(chofer.id(), id, request.estado());
	}

	@PutMapping("/{id}/ubicacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Reportar la ubicación del chofer", description = "Solo en camino o de regreso; avisa al "
			+ "cliente (traslado.ubicacion). Responde 400 si está fuera de rango, 404 si no existe y 409 con "
			+ "TRANSICION_INVALIDA si el traslado no va en ruta.")
	public void reportarUbicacion(@PathVariable Long id, @Valid @RequestBody UbicacionChoferRequest request) {
		trasladosAppService.reportarUbicacion(id, request.lat(), request.lng());
	}

	@PutMapping(path = "/{id}/checklist", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Cargar el checklist de un traslado", description = "multipart/form-data: km (1 a "
			+ "2 000 000), gasolina (vacio, un_cuarto, medio, tres_cuartos, lleno), notas (hasta 500), fotos (0 a 10 "
			+ "imágenes JPEG, PNG o WebP) y firma (imagen). Reemplaza el checklist anterior y borra sus archivos. "
			+ "Responde 400 si un dato no es válido, 404 si el traslado no existe y 409 con TRANSICION_INVALIDA si "
			+ "sigue programado o está cancelado.")
	public ChecklistTrasladoApp cargarChecklist(@PathVariable Long id, @ModelAttribute CargarChecklistRequest request) {
		List<ArchivoSubido> fotos = request.getFotos().stream().filter(foto -> !foto.isEmpty())
				.map(TrasladosPersonalController::leer).toList();
		MultipartFile firma = request.getFirma();
		return checklistService.cargar(id, request.getKm(), request.getGasolina(), request.getNotas(), fotos,
				firma == null || firma.isEmpty() ? null : leer(firma));
	}

	private static ArchivoSubido leer(MultipartFile archivo) {
		try {
			return new ArchivoSubido(archivo.getContentType(), archivo.getBytes());
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo leer el archivo", ex);
		}
	}
}
