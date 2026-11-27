package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SucursalApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SucursalesAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sucursales con ubicación y horarios (V2-M27.02). */
@RestController
@Tag(name = "Sucursales", description = "Dónde y cuándo atiende la agencia")
public class SucursalesAppController {

	private final SucursalesAppService sucursales;

	public SucursalesAppController(SucursalesAppService sucursales) {
		this.sucursales = sucursales;
	}

	@GetMapping("/api/v1/app/sucursales")
	@PreAuthorize("hasRole('CLIENTE_CARECAR')")
	@Operation(summary = "Sucursales activas", description = "La principal primero. «Abierto ahora» va calculado con "
			+ "la hora de la agencia. Puede venir vacía.")
	public List<SucursalApp> listar() {
		return sucursales.listar();
	}
}
