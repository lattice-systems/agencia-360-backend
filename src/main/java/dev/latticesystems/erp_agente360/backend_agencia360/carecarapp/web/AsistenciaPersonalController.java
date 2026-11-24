package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AsistenciaAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudAsistenciaApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Atención de las solicitudes de asistencia por el personal (V2-M11.04). Usa el
 * rol de marketing mientras el ERP siga congelado.
 */
@RestController
@RequestMapping("/api/v1/asistencia")
@Tag(name = "Asistencia (personal)", description = "El personal mueve las solicitudes de ayuda del cliente")
public class AsistenciaPersonalController {

	private final AsistenciaAppService asistenciaAppService;

	public AsistenciaPersonalController(AsistenciaAppService asistenciaAppService) {
		this.asistenciaAppService = asistenciaAppService;
	}

	@PatchMapping("/{id}/estado")
	@PreAuthorize("hasRole('GERENTE_MARKETING')")
	@Operation(summary = "Cambiar el estado de una solicitud de asistencia", description = "estado: en_camino, "
			+ "atendida o cancelada. Avisa en vivo al cliente dueño (evento asistencia.estado). Al cerrarse se borra "
			+ "la ubicación. Responde 400 si el estado no existe, 404 si la solicitud no existe y 409 con "
			+ "TRANSICION_INVALIDA si el paso no está permitido.")
	public SolicitudAsistenciaApp cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request) {
		return asistenciaAppService.cambiarEstado(id, request.estado());
	}
}
