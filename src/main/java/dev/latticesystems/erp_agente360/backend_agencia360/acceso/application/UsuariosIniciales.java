package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea al arrancar el primer Administrador (ERP) y el primer Gerente de
 * marketing (CRM), para que alguien pueda entrar y dar de alta al resto. Solo
 * actúa si las variables de entorno correspondientes tienen valor y el usuario
 * todavía no existe; nunca modifica un usuario existente. Las contraseñas se
 * pasan por variables de entorno, no se guardan en el código.
 */
@Component
public class UsuariosIniciales implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(UsuariosIniciales.class);

	private final UsuarioRepository usuarioRepository;
	private final RolRepository rolRepository;
	private final PasswordEncoder passwordEncoder;
	private final String administradorUsuario;
	private final String administradorContrasena;
	private final String gerenteUsuario;
	private final String gerenteContrasena;

	public UsuariosIniciales(UsuarioRepository usuarioRepository, RolRepository rolRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.acceso.administrador-inicial.usuario:}") String administradorUsuario,
			@Value("${app.acceso.administrador-inicial.contrasena:}") String administradorContrasena,
			@Value("${app.acceso.gerente-inicial.usuario:}") String gerenteUsuario,
			@Value("${app.acceso.gerente-inicial.contrasena:}") String gerenteContrasena) {
		this.usuarioRepository = usuarioRepository;
		this.rolRepository = rolRepository;
		this.passwordEncoder = passwordEncoder;
		this.administradorUsuario = administradorUsuario;
		this.administradorContrasena = administradorContrasena;
		this.gerenteUsuario = gerenteUsuario;
		this.gerenteContrasena = gerenteContrasena;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		crearSiFalta(administradorUsuario, administradorContrasena, "Administrador inicial", "ADMINISTRADOR");
		crearSiFalta(gerenteUsuario, gerenteContrasena, "Gerente de marketing inicial", "GERENTE_MARKETING");
	}

	private void crearSiFalta(String usuario, String contrasena, String nombre, String rol) {
		if (usuario.isBlank() || contrasena.isBlank() || usuarioRepository.existsByUsuario(usuario)) {
			return;
		}
		rolRepository.findByNombre(rol).ifPresent(encontrado -> {
			usuarioRepository.save(new Usuario(usuario, passwordEncoder.encode(contrasena), nombre, encontrado));
			LOG.info("Usuario inicial {} creado con el rol {}", usuario, rol);
		});
	}
}
