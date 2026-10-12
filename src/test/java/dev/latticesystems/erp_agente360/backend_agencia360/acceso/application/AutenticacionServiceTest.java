package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Aplicacion;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoAutenticadoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

	private static final String CONTRASENA = "Secreta123";

	private static final Rol CAJA = new Rol("CAJA", "Registro de cobros", true, false);
	private static final Rol VENTAS = new Rol("VENTAS", "Vendedor", false, true);
	private static final Rol CONTABILIDAD = new Rol("CONTABILIDAD", "Contabilidad", true, true);

	@Mock
	private UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final JwtService jwtService = new JwtService("test-only-secret-0123456789-0123456789-0123456789", 60);

	private AutenticacionService service;

	@BeforeEach
	void setUp() {
		service = new AutenticacionService(usuarioRepository, passwordEncoder, jwtService);
	}

	@Test
	void iniciaSesionConCredencialesCorrectasYDevuelveTokenConElRol() {
		registrar("caja1", CAJA);

		SesionIniciada sesion = service.iniciarSesion("caja1", CONTRASENA, Aplicacion.ERP);

		assertThat(sesion.rol()).isEqualTo(Role.CAJA);
		assertThat(sesion.aplicacion()).isEqualTo(Aplicacion.ERP);
		var claims = jwtService.parseClaims(sesion.accessToken());
		assertThat(claims.getSubject()).isEqualTo("caja1");
		assertThat(jwtService.extractAuthorities(claims)).extracting("authority").containsExactly("ROLE_CAJA");
	}

	@Test
	void rechazaContrasenaIncorrecta() {
		registrar("caja1", CAJA);

		assertThatThrownBy(() -> service.iniciarSesion("caja1", "otra", Aplicacion.ERP))
				.isInstanceOf(NoAutenticadoException.class).hasMessage(AutenticacionService.CREDENCIALES_INVALIDAS);
	}

	@Test
	void rechazaUsuarioInexistenteConElMismoMensaje() {
		when(usuarioRepository.buscarPorUsuarioConRol("nadie")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.iniciarSesion("nadie", CONTRASENA, Aplicacion.ERP))
				.isInstanceOf(NoAutenticadoException.class).hasMessage(AutenticacionService.CREDENCIALES_INVALIDAS);
	}

	@Test
	void rechazaUsuarioInactivo() {
		Usuario usuario = registrar("caja1", CAJA);
		ReflectionTestUtils.setField(usuario, "activo", false);

		assertThatThrownBy(() -> service.iniciarSesion("caja1", CONTRASENA, Aplicacion.ERP))
				.isInstanceOf(NoAutenticadoException.class);
	}

	@Test
	void rechazaRolSinAccesoALaAplicacion() {
		registrar("vendedor1", VENTAS);

		assertThatThrownBy(() -> service.iniciarSesion("vendedor1", CONTRASENA, Aplicacion.ERP))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void contabilidadEntraAlErpYAlCrm() {
		registrar("conta1", CONTABILIDAD);

		assertThat(service.iniciarSesion("conta1", CONTRASENA, Aplicacion.ERP).rol()).isEqualTo(Role.CONTABILIDAD);
		assertThat(service.iniciarSesion("conta1", CONTRASENA, Aplicacion.CRM).rol()).isEqualTo(Role.CONTABILIDAD);
	}

	private Usuario registrar(String nombreUsuario, Rol rol) {
		Usuario usuario = new Usuario(nombreUsuario, passwordEncoder.encode(CONTRASENA), "Persona de prueba", rol);
		when(usuarioRepository.buscarPorUsuarioConRol(nombreUsuario)).thenReturn(Optional.of(usuario));
		return usuario;
	}
}
