package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CatalogoFiscalApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EntradaPerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PerfilFiscalApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.PerfilesFiscalesAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Datos fiscales del cliente y los catálogos del SAT que usan (V2-M08.05). */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('CLIENTE_CARECAR')")
@Tag(name = "App CareCar - Pagos y facturas", description = "Datos fiscales, pagos y facturas simulados (ADR 0009)")
public class PerfilesFiscalesAppController {

	private static final String SI_FALLA = "Responde 400 con DATOS_FISCALES_INVALIDOS y campos (qué corregir en "
			+ "cada uno) si un dato no cumple las reglas del SAT.";

	private final PerfilesFiscalesAppService perfiles;

	public PerfilesFiscalesAppController(PerfilesFiscalesAppService perfiles) {
		this.perfiles = perfiles;
	}

	@GetMapping("/catalogos-fiscales")
	@Operation(summary = "Catálogos del SAT para los datos fiscales", description = "Regímenes y usos de CFDI 4.0 "
			+ "con la persona a la que aplican y los regímenes que acepta cada uso.")
	public CatalogoFiscalApp catalogos() {
		return CatalogoFiscalApp.completo();
	}

	@GetMapping("/perfiles-fiscales")
	@Operation(summary = "Listar mis datos fiscales", description = "El predeterminado primero.")
	public List<PerfilFiscalApp> listar(@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return perfiles.listar(cliente.id());
	}

	@GetMapping("/perfiles-fiscales/{id}")
	@Operation(summary = "Ver un perfil fiscal", description = "Responde 404 si no es del cliente.")
	public PerfilFiscalApp uno(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		return perfiles.uno(cliente.id(), id);
	}

	@PostMapping("/perfiles-fiscales")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Agregar datos fiscales", description = "El primero queda como predeterminado. " + SI_FALLA)
	public PerfilFiscalApp crear(@RequestBody EntradaPerfilFiscal entrada,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return perfiles.crear(cliente.id(), entrada);
	}

	@PutMapping("/perfiles-fiscales/{id}")
	@Operation(summary = "Corregir datos fiscales", description = "Responde 404 si no es del cliente. " + SI_FALLA)
	public PerfilFiscalApp editar(@PathVariable Long id, @RequestBody EntradaPerfilFiscal entrada,
			@AuthenticationPrincipal UsuarioAutenticado cliente) {
		return perfiles.editar(cliente.id(), id, entrada);
	}

	@DeleteMapping("/perfiles-fiscales/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Borrar datos fiscales", description = "Responde 404 si no es del cliente. Si era el "
			+ "predeterminado, lo toma el más viejo de los que quedan.")
	public void borrar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado cliente) {
		perfiles.borrar(cliente.id(), id);
	}
}
