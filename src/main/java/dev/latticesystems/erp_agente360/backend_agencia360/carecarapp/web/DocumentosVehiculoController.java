package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentosVehiculoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/app/vehiculos/{vehiculoId}/documentos")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
public class DocumentosVehiculoController {

	private final DocumentosVehiculoService documentos;

	public DocumentosVehiculoController(DocumentosVehiculoService documentos) {
		this.documentos = documentos;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentoApp guardar(@PathVariable Long vehiculoId, @AuthenticationPrincipal UsuarioAutenticado cliente,
			@RequestParam TipoDocumento tipo, @RequestParam(required = false) String titulo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate venceEn,
			@RequestParam(required = false) MultipartFile archivo) throws IOException {
		if (archivo == null || archivo.getSize() > DocumentosVehiculoService.TAMANO_MAXIMO
				|| !MediaType.IMAGE_JPEG_VALUE.equals(archivo.getContentType())) {
			throw new IllegalArgumentException("Selecciona una imagen JPEG de hasta 5 MB");
		}
		return documentos.guardar(cliente.id(), vehiculoId, tipo, titulo, venceEn, archivo.getBytes());
	}

}
