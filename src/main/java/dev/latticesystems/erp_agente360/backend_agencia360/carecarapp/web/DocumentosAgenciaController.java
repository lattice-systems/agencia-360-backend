package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.DocumentosVehiculoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDocumento;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentosAgenciaController {

	private final DocumentosVehiculoService documentos;

	public DocumentosAgenciaController(DocumentosVehiculoService documentos) {
		this.documentos = documentos;
	}

	@PostMapping(path = "/api/v1/agencia/vehiculos/{vehiculoId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('FACTURACION', 'ADMINISTRADOR')")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentoApp publicar(@PathVariable Long vehiculoId, @RequestParam TipoDocumento tipo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate venceEn,
			@RequestParam(required = false) MultipartFile archivo) throws IOException {
		if (archivo == null || archivo.getSize() > DocumentosVehiculoService.TAMANO_MAXIMO
				|| !MediaType.APPLICATION_PDF_VALUE.equals(archivo.getContentType())) {
			throw new IllegalArgumentException("Selecciona un PDF de hasta 5 MB");
		}
		return documentos.guardarDeAgencia(vehiculoId, tipo, venceEn, archivo.getBytes());
	}
}
