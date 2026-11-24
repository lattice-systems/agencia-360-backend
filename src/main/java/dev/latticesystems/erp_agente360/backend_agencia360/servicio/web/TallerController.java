package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.application.TallerService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
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
 * La orden en el taller del lado de Servicio (V2-M04.01 a V2-M04.05). El ERP
 * está congelado: mientras no tenga pantalla, el taller usa estas rutas para
 * abrir la orden, pasarla de etapa, registrar la inspección con fotos y videos
 * y proponer trabajos adicionales. El cliente lo ve en la app.
 */
@RestController
@RequestMapping("/api/v1/taller/ordenes")
@Tag(name = "Taller en vivo", description = "Órdenes en el taller: etapas, inspección y trabajos adicionales")
public class TallerController {

	private final TallerService tallerService;

	public TallerController(TallerService tallerService) {
		this.tallerService = tallerService;
	}

	@PostMapping
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Abrir una orden", description = "El vehículo entra al taller: la orden queda RECIBIDO, "
			+ "con montos en cero y a nombre de quien la abre. Responde 400 si un dato no es válido o la entrega "
			+ "estimada ya pasó, 404 si el vehículo no existe y 409 ORDEN_ABIERTA si ya tiene una orden sin "
			+ "entregar.")
	public ResponseEntity<OrdenEnVivo> abrir(@Valid @RequestBody AbrirOrdenRequest request,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		OrdenEnVivo orden = tallerService.abrir(request.vehiculoId(), request.kilometraje(), request.citaId(),
				request.entregaEstimada(), actor);
		return ResponseEntity.created(URI.create("/api/v1/taller/ordenes/" + orden.id())).body(orden);
	}

	@GetMapping("/{ordenId}")
	@PreAuthorize("hasAnyRole('SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Consultar una orden", description = "Etapas, inspección, evidencias y trabajos "
			+ "adicionales con sus totales. Responde 404 si no existe.")
	public OrdenEnVivo consultar(@PathVariable Long ordenId) {
		return tallerService.consultar(ordenId);
	}

	@PostMapping("/{ordenId}/etapas")
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Pasar la orden a otra etapa", description = "Termina la etapa en curso y empieza la "
			+ "pedida; se puede saltar etapas pero nunca volver. LISTO_PARA_ENTREGA la deja TERMINADO y "
			+ "ENTREGADO la cierra. Responde 400 si la etapa no va después de la actual, 404 si la orden no "
			+ "existe y 409 ORDEN_CERRADA si ya se entregó.")
	public OrdenEnVivo avanzar(@PathVariable Long ordenId, @Valid @RequestBody AvanzarEtapaRequest request) {
		return tallerService.avanzar(ordenId, request.etapa(), request.nota(), request.entregaEstimada());
	}

	@PostMapping("/{ordenId}/inspeccion")
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Registrar un punto de la inspección", description = "Con su semáforo (VERDE, AMARILLO "
			+ "o ROJO) y un comentario opcional. Responde 400 si un dato no es válido y 404 si la orden no "
			+ "existe.")
	public OrdenEnVivo revisar(@PathVariable Long ordenId, @Valid @RequestBody RevisarPuntoRequest request) {
		return tallerService.revisar(ordenId, request.punto(), request.resultado(), request.comentario());
	}

	@PostMapping(path = "/{ordenId}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Subir una foto o un video", description = "Foto JPEG, PNG o WebP, o video MP4, de 20 "
			+ "MB como máximo, de la orden o de un punto revisado (puntoInspeccionId). Responde 400 si el "
			+ "archivo no es válido, 404 si la orden o el punto no existen y 413 si pasa del tamaño.")
	public ResponseEntity<OrdenEnVivo> agregarEvidencia(@PathVariable Long ordenId,
			@RequestParam("archivo") MultipartFile archivo, @RequestParam(required = false) Long puntoInspeccionId) {
		byte[] contenido;
		try {
			contenido = archivo.getBytes();
		} catch (IOException ex) {
			throw new UncheckedIOException("No se pudo leer la evidencia", ex);
		}
		return ResponseEntity.status(201)
				.body(tallerService.agregarEvidencia(ordenId, puntoInspeccionId, archivo.getContentType(), contenido));
	}

	@PostMapping("/{ordenId}/adicionales")
	@PreAuthorize("hasRole('SERVICIO')")
	@Operation(summary = "Proponer un trabajo adicional", description = "El cliente lo aprueba o lo rechaza con su "
			+ "firma desde la app. Los montos traen el IVA incluido y el total es su suma. Responde 400 si un dato "
			+ "no es válido, 404 si la orden no existe y 409 ORDEN_CERRADA si ya está terminada.")
	public ResponseEntity<OrdenEnVivo> proponer(@PathVariable Long ordenId,
			@Valid @RequestBody ProponerAdicionalRequest request) {
		return ResponseEntity.status(201)
				.body(tallerService.proponer(ordenId, request.concepto(), request.manoDeObra(), request.refacciones()));
	}
}
