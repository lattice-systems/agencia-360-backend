package dev.latticesystems.erp_agente360.backend_agencia360.archivos.web;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.application.ArchivosService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/archivos")
@Tag(name = "Archivos", description = "Descarga de archivos con URL temporal firmada")
public class ArchivoController {

	private static final String NO_EXISTE = "El archivo no existe o el enlace ya venció";

	private final ArchivosService archivosService;

	public ArchivoController(ArchivosService archivosService) {
		this.archivosService = archivosService;
	}

	@GetMapping("/{carpeta}/{id}")
	@SecurityRequirements
	@Operation(summary = "Descargar un archivo con su URL temporal", description = "La URL la arma el backend con "
			+ "expira y firma (HMAC) y vence a los 15 minutos. Responde 404 si no existe, la firma no es válida o "
			+ "ya venció.")
	public ResponseEntity<byte[]> descargar(@PathVariable String carpeta, @PathVariable String id,
			@RequestParam(defaultValue = "0") long expira, @RequestParam(required = false) String firma) {
		return archivosService.descargar(carpeta + "/" + id, expira, firma)
				.map(archivo -> ResponseEntity.ok().contentType(MediaType.parseMediaType(archivo.tipoContenido()))
						.cacheControl(CacheControl.noStore())
						.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
						.body(archivo.contenido()))
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
	}
}
