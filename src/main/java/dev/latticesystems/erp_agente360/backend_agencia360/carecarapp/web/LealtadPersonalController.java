package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CanjeApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.LealtadAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El personal usa en la agencia el canje que el cliente muestra (V2-M12.03),
 * mientras el ERP siga congelado.
 */
@RestController
@RequestMapping("/api/v1/lealtad")
@Tag(name = "Lealtad (personal)", description = "Uso de canjes en la agencia")
public class LealtadPersonalController {

	private final LealtadAppService lealtad;

	public LealtadPersonalController(LealtadAppService lealtad) {
		this.lealtad = lealtad;
	}

	@PostMapping("/canjes/{codigo}/usar")
	@PreAuthorize("hasAnyRole('CAJA', 'SERVICIO', 'ADMINISTRADOR')")
	@Operation(summary = "Usar un canje", description = "Marca usado el canje del código. Responde 404 si no existe "
			+ "y 409 CANJE_NO_VIGENTE si ya se usó o venció.")
	public CanjeApp usar(@PathVariable String codigo) {
		return lealtad.usar(codigo);
	}
}
