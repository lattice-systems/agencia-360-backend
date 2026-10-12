package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Aplicacion;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios y asignación de roles (ERP HU 0.1 y CRM HU 0.1). Cada
 * administrador trabaja solo sobre su aplicación: el Administrador sobre el ERP
 * y el Gerente de marketing sobre el CRM. Solo ve y asigna roles con acceso a
 * esa aplicación.
 */
@Service
public class AdministracionUsuariosService {

	private final UsuarioRepository usuarioRepository;
	private final RolRepository rolRepository;
	private final PasswordEncoder passwordEncoder;

	public AdministracionUsuariosService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
			PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.rolRepository = rolRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<RolVista> rolesAsignables(Set<Role> rolesDelActor) {
		Aplicacion aplicacion = aplicacionAdministrada(rolesDelActor);
		return rolRepository.findAllByOrderByNombreAsc().stream().filter(rol -> rol.daAccesoA(aplicacion))
				.map(RolVista::de).toList();
	}

	@Transactional(readOnly = true)
	public List<UsuarioVista> listar(Set<Role> rolesDelActor) {
		List<Usuario> usuarios = aplicacionAdministrada(rolesDelActor) == Aplicacion.ERP
				? usuarioRepository.buscarConAccesoAlErp()
				: usuarioRepository.buscarConAccesoAlCrm();
		return usuarios.stream().map(UsuarioVista::de).toList();
	}

	@Transactional
	public UsuarioVista crear(NuevoUsuario nuevo, Set<Role> rolesDelActor) {
		Aplicacion aplicacion = aplicacionAdministrada(rolesDelActor);
		if (usuarioRepository.existsByUsuario(nuevo.usuario())) {
			throw new ConflictoException("Ya existe un usuario con el nombre de usuario " + nuevo.usuario());
		}
		Rol rol = rolAsignable(nuevo.rol(), aplicacion);
		Usuario usuario = new Usuario(nuevo.usuario(), passwordEncoder.encode(nuevo.contrasena()), nuevo.nombre(), rol);
		return UsuarioVista.de(usuarioRepository.save(usuario));
	}

	@Transactional
	public UsuarioVista cambiarRol(Long usuarioId, String nuevoRol, Set<Role> rolesDelActor) {
		Aplicacion aplicacion = aplicacionAdministrada(rolesDelActor);
		Usuario usuario = usuarioRepository.buscarPorIdConRol(usuarioId).filter(u -> u.getRol().daAccesoA(aplicacion))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + usuarioId));
		usuario.cambiarRol(rolAsignable(nuevoRol, aplicacion));
		return UsuarioVista.de(usuario);
	}

	private Rol rolAsignable(String nombre, Aplicacion aplicacion) {
		Rol rol = rolRepository.findByNombre(nombre)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + nombre));
		if (!rol.daAccesoA(aplicacion)) {
			throw new AccessDeniedException("No puedes asignar el rol " + nombre + " desde el " + aplicacion);
		}
		return rol;
	}

	/** El Administrador administra el ERP y el Gerente de marketing el CRM. */
	static Aplicacion aplicacionAdministrada(Set<Role> rolesDelActor) {
		if (rolesDelActor.contains(Role.ADMINISTRADOR)) {
			return Aplicacion.ERP;
		}
		if (rolesDelActor.contains(Role.GERENTE_MARKETING)) {
			return Aplicacion.CRM;
		}
		throw new AccessDeniedException("Tu rol no administra usuarios");
	}
}
