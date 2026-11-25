package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ArcoService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Derechos ARCO desde la app: acceso a mis datos (M22.02). */
@RestController
@RequestMapping("/api/v1/app/privacidad/arco")
@Tag(name = "App CareCar · Privacidad")
public class ArcoController {

	private final ArcoService arco;

	public ArcoController(ArcoService arco) {
		this.arco = arco;
	}

	@GetMapping
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Mis solicitudes de derechos ARCO", description = "V2-M22.02 · Las últimas 20, más recientes "
			+ "primero. Una de acceso RESUELTA trae su archivo hasta venceEn; después es VENCIDA.")
	public List<ArcoService.SolicitudArcoApp> mias(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return arco.misSolicitudes(cliente.id());
	}

	@PostMapping("/acceso")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Pedir una copia de mis datos", description = "V2-M22.02 · 202 y el archivo se prepara sin "
			+ "esperar; al estar listo llega el evento arco.estado. Si ya hay una de acceso pendiente, la devuelve "
			+ "con 200.")
	public ResponseEntity<ArcoService.SolicitudArcoApp> pedirAcceso(
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		ArcoService.Resultado resultado = arco.pedirAcceso(cliente.id());
		return ResponseEntity.status(resultado.nueva() ? HttpStatus.ACCEPTED : HttpStatus.OK)
				.body(resultado.solicitud());
	}

	@GetMapping("/{solicitudId}/archivo")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Descargar el archivo de una solicitud de acceso", description = "V2-M22.02 · Solo la "
			+ "cuenta dueña, con su sesión; sin enlace público ni caché. 404 si no existe, es de otra cuenta, no está "
			+ "resuelta o ya venció.")
	public ResponseEntity<byte[]> archivo(@PathVariable Long solicitudId,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		ArcoService.ArchivoArco archivo = arco.archivo(cliente.id(), solicitudId);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
				.body(archivo.contenido());
	}
}
