package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentosAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Documentos del vehículo con su estado de vencimiento (V2-M09.01). */
@RestController
@Tag(name = "Documentos", description = "Póliza, tarjeta de circulación, verificación y más, con su vencimiento")
public class DocumentosAppController {

	private final DocumentosAppService documentos;

	public DocumentosAppController(DocumentosAppService documentos) {
		this.documentos = documentos;
	}

	@GetMapping("/api/v1/app/vehiculos/{vehiculoId}/documentos")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Documentos del vehículo", description = "Por vencimiento, los que no tienen fecha al "
			+ "final. El estado va calculado con la fecha de la agencia. Puede venir vacía. Responde 404 si el "
			+ "vehículo no es del cliente.")
	public List<DocumentoApp> listar(@PathVariable Long vehiculoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return documentos.listar(cliente.id(), vehiculoId);
	}
}
