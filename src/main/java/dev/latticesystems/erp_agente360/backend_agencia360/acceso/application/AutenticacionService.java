package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Aplicacion;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoAutenticadoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión del personal en el ERP o en el CRM (ERP HU 0.1 y CRM HU
 * 0.1). Las credenciales se validan contra el hash guardado y el rol decide si
 * el usuario puede entrar a la aplicación que pidió.
 */
@Service
public class AutenticacionService {

	/**
	 * Mismo mensaje para usuario inexistente, inactivo o contraseña incorrecta: no
	 * revela cuál falló.
	 */
	static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AutenticacionService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional(readOnly = true)
	public SesionIniciada iniciarSesion(String usuario, String contrasena, Aplicacion aplicacion) {
		Usuario encontrado = usuarioRepository.buscarPorUsuarioConRol(usuario).filter(Usuario::isActivo)
				.filter(u -> passwordEncoder.matches(contrasena, u.getContrasenaHash()))
				.orElseThrow(() -> new NoAutenticadoException(CREDENCIALES_INVALIDAS));

		if (!encontrado.getRol().daAccesoA(aplicacion)) {
			throw new AccessDeniedException("Tu rol no tiene acceso al " + aplicacion);
		}

		Role rol = encontrado.getRol().comoRole();
		String token = jwtService.generateToken(encontrado.getId(), encontrado.getUsuario(), List.of(rol));
		return new SesionIniciada(token, encontrado.getUsuario(), encontrado.getNombre(), rol, aplicacion);
	}
}
