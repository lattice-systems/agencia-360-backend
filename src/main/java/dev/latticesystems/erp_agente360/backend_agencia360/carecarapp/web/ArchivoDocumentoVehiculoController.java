package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentosVehiculoService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArchivoDocumentoVehiculoController {

	private final DocumentosVehiculoService documentos;

	public ArchivoDocumentoVehiculoController(DocumentosVehiculoService documentos) {
		this.documentos = documentos;
	}

	@GetMapping("/api/v1/app/documentos/{documentoId}/archivo")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	public ResponseEntity<Void> descargar(@PathVariable Long documentoId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		URI url = URI.create(documentos.urlArchivo(cliente.id(), documentoId));
		return ResponseEntity.status(HttpStatus.FOUND).location(url).cacheControl(CacheControl.noStore()).build();
	}
}
