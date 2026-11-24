package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AsistenteService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RespuestaAsistenteApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RespuestaTestigoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Asistente CareCar (V2-M24.03): preguntas sobre el manual del vehículo. */
@RestController
@RequestMapping("/api/v1/app/asistente")
@Tag(name = "App CareCar · Asistente")
public class AsistenteController {

	private final AsistenteService asistenteService;

	public AsistenteController(AsistenteService asistenteService) {
		this.asistenteService = asistenteService;
	}

	@PostMapping("/preguntas")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Preguntar al asistente sobre el manual del vehículo", description = "Responde con base "
			+ "en el manual del modelo y año del vehículo, citando páginas; encontrado=false si el manual no lo "
			+ "dice. 404 si el vehículo no es del cliente; 409 ASISTENTE_SIN_MANUAL; 429 ASISTENTE_LIMITE con "
			+ "Retry-After; 503 ASISTENTE_NO_DISPONIBLE.")
	public RespuestaAsistenteApp preguntar(@Valid @RequestBody PreguntaAsistenteRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return asistenteService.preguntar(cliente.id(), request.vehiculoId(), request.pregunta());
	}

	@PostMapping("/consultas/{consultaId}/calificacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Calificar una respuesta del asistente", description = "V2-M24.07 · Guarda si la "
			+ "respuesta fue útil (👍/👎); calificar otra vez la reemplaza. 404 si la consulta no existe o no es "
			+ "del cliente.")
	public void calificar(@PathVariable Long consultaId, @Valid @RequestBody CalificacionAsistenteRequest request,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		asistenteService.calificar(cliente.id(), consultaId, request.util());
	}

	private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
	private static final Set<String> TIPOS = Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

	@PostMapping(path = "/testigos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Identificar una luz del tablero con una foto", description = "V2-M24.05 · La foto "
			+ "se limpia (sin EXIF) y no se guarda. Responde testigo, gravedad (roja, amarilla, informativa), qué "
			+ "hacer y la página del manual si existe; reconocido=false si no lo identifica. 400 si no es JPEG o "
			+ "PNG de hasta 5 MB; 404 vehículo ajeno; 429 ASISTENTE_LIMITE; 503 ASISTENTE_NO_DISPONIBLE.")
	public RespuestaTestigoApp identificarTestigo(@RequestParam Long vehiculoId, @RequestParam MultipartFile imagen,
			@AuthenticationPrincipal UsuarioAutenticado cliente) throws IOException {
		if (imagen.isEmpty() || imagen.getSize() > TAMANO_MAXIMO || !TIPOS.contains(imagen.getContentType())) {
			throw new SolicitudInvalidaException("La foto debe ser una imagen JPEG o PNG de hasta 5 MB",
					CodigosError.DATOS_INVALIDOS);
		}
		return asistenteService.identificarTestigo(cliente.id(), vehiculoId, imagen.getBytes());
	}
}
