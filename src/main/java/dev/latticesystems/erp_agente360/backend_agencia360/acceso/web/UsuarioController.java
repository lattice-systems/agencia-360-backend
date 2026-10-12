package dev.latticesystems.erp_agente360.backend_agencia360.acceso.web;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.application.AdministracionUsuariosService;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.application.RolVista;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.application.UsuarioVista;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de usuarios. El Administrador gestiona usuarios del ERP y el
 * Gerente de marketing usuarios del CRM; cada uno solo ve y asigna roles de su
 * aplicación.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE_MARKETING')")
@Tag(name = "Usuarios", description = "Alta de usuarios y asignación de roles")
public class UsuarioController {

	private final AdministracionUsuariosService administracionUsuariosService;

	public UsuarioController(AdministracionUsuariosService administracionUsuariosService) {
		this.administracionUsuariosService = administracionUsuariosService;
	}

	@GetMapping("/roles")
	@Operation(summary = "Roles que el usuario autenticado puede asignar")
	public List<RolVista> roles(Authentication authentication) {
		return administracionUsuariosService.rolesAsignables(RolesDelActor.de(authentication));
	}

	@GetMapping("/usuarios")
	@Operation(summary = "Usuarios de la aplicación que administra el usuario autenticado")
	public List<UsuarioVista> listar(Authentication authentication) {
		return administracionUsuariosService.listar(RolesDelActor.de(authentication));
	}

	@PostMapping("/usuarios")
	@Operation(summary = "Dar de alta un usuario", description = "Responde 409 si el nombre de usuario ya existe y 403 "
			+ "si el rol no pertenece a la aplicación que administra quien hace la petición.")
	public ResponseEntity<UsuarioVista> crear(@Valid @RequestBody CrearUsuarioRequest request,
			Authentication authentication) {
		UsuarioVista creado = administracionUsuariosService.crear(request.comoNuevoUsuario(),
				RolesDelActor.de(authentication));
		return ResponseEntity.created(URI.create("/api/v1/usuarios/" + creado.id())).body(creado);
	}

	@PutMapping("/usuarios/{id}/rol")
	@Operation(summary = "Cambiar el rol de un usuario")
	public UsuarioVista cambiarRol(@PathVariable Long id, @Valid @RequestBody CambiarRolRequest request,
			Authentication authentication) {
		return administracionUsuariosService.cambiarRol(id, request.rol(), RolesDelActor.de(authentication));
	}
}
