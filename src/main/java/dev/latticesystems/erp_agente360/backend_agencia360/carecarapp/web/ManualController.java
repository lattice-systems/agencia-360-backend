package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ManualVista;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ManualesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carga de los manuales de la app (APP-3.1). Los sube el Gerente de marketing;
 * la pantalla para hacerlo llega en otra historia y mientras tanto los carga el
 * script de datos de ejemplo.
 */
@RestController
@RequestMapping("/api/v1/manuales")
@Tag(name = "Manuales", description = "Manuales de propietario que ven los clientes en la app")
public class ManualController {

	private final ManualesService manualesService;

	public ManualController(ManualesService manualesService) {
		this.manualesService = manualesService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Subir el manual de una marca, tipo y modelo", description = "Solo PDF, de 20 MB como "
			+ "máximo. Si ya hay un manual de esa marca, tipo y modelo, lo reemplaza. Responde 400 si un dato no es "
			+ "válido o el archivo no es un PDF, y 413 si pasa del tamaño.")
	public ManualVista subir(@Valid @ModelAttribute SubirManualRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		byte[] contenido;
		try {
			contenido = request.getArchivo().getBytes();
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo leer el manual", ex);
		}
		return manualesService.subir(request.getMarca(), request.getModelo(), request.getAnio(), contenido, actor);
	}
}
