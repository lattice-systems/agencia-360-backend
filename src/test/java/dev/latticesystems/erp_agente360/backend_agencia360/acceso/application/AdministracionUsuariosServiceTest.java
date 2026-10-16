package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdministracionUsuariosServiceTest {

	private static final Set<Role> ADMINISTRADOR = Set.of(Role.ADMINISTRADOR);
	private static final Set<Role> GERENTE = Set.of(Role.GERENTE_MARKETING);

	private static final Rol CAJA = new Rol("CAJA", "Registro de cobros", true, false);
	private static final Rol VENTAS = new Rol("VENTAS", "Vendedor", false, true);
	private static final Rol CONTABILIDAD = new Rol("CONTABILIDAD", "Contabilidad", true, false);

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private RolRepository rolRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	private AdministracionUsuariosService service;

	@BeforeEach
	void setUp() {
		service = new AdministracionUsuariosService(usuarioRepository, rolRepository, passwordEncoder);
	}

	@Test
	void elAdministradorCreaUsuariosDelErpConLaContrasenaHasheada() {
		when(usuarioRepository.existsByUsuario("caja1")).thenReturn(false);
		when(rolRepository.findByNombre("CAJA")).thenReturn(Optional.of(CAJA));
		when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UsuarioVista creado = service.crear(new NuevoUsuario("caja1", "Secreta123", "Cajero Uno", "CAJA"),
				ADMINISTRADOR);

		assertThat(creado.usuario()).isEqualTo("caja1");
		assertThat(creado.rol()).isEqualTo("CAJA");
		assertThat(creado.activo()).isTrue();
		ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarioRepository).save(guardado.capture());
		assertThat(guardado.getValue().getContrasenaHash()).isNotEqualTo("Secreta123");
		assertThat(passwordEncoder.matches("Secreta123", guardado.getValue().getContrasenaHash())).isTrue();
	}

	@Test
	void elAdministradorNoPuedeAsignarRolesDelCrm() {
		when(usuarioRepository.existsByUsuario("vendedor1")).thenReturn(false);
		when(rolRepository.findByNombre("VENTAS")).thenReturn(Optional.of(VENTAS));

		assertThatThrownBy(
				() -> service.crear(new NuevoUsuario("vendedor1", "Secreta123", "Vendedor", "VENTAS"), ADMINISTRADOR))
				.isInstanceOf(AccessDeniedException.class);
		verify(usuarioRepository, never()).save(any());
	}

	@Test
	void elGerenteDeMarketingCreaVendedores() {
		when(usuarioRepository.existsByUsuario("vendedor1")).thenReturn(false);
		when(rolRepository.findByNombre("VENTAS")).thenReturn(Optional.of(VENTAS));
		when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UsuarioVista creado = service.crear(new NuevoUsuario("vendedor1", "Secreta123", "Vendedor", "VENTAS"), GERENTE);

		assertThat(creado.rol()).isEqualTo("VENTAS");
	}

	@Test
	void elGerenteDeMarketingNoPuedeAsignarRolesDelErp() {
		when(usuarioRepository.existsByUsuario("caja1")).thenReturn(false);
		when(rolRepository.findByNombre("CAJA")).thenReturn(Optional.of(CAJA));

		assertThatThrownBy(() -> service.crear(new NuevoUsuario("caja1", "Secreta123", "Cajero", "CAJA"), GERENTE))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void rechazaNombreDeUsuarioRepetido() {
		when(usuarioRepository.existsByUsuario("caja1")).thenReturn(true);

		assertThatThrownBy(
				() -> service.crear(new NuevoUsuario("caja1", "Secreta123", "Cajero", "CAJA"), ADMINISTRADOR))
				.isInstanceOf(ConflictoException.class);
		verify(usuarioRepository, never()).save(any());
	}

	@Test
	void rechazaRolInexistente() {
		when(usuarioRepository.existsByUsuario("nuevo")).thenReturn(false);
		when(rolRepository.findByNombre("SUPERVISOR")).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> service.crear(new NuevoUsuario("nuevo", "Secreta123", "Nuevo", "SUPERVISOR"), ADMINISTRADOR))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unRolQueNoAdministraUsuariosNoPuedeCrearlos() {
		assertThatThrownBy(
				() -> service.crear(new NuevoUsuario("nuevo", "Secreta123", "Nuevo", "CAJA"), Set.of(Role.CAJA)))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void cadaAdministradorSoloVeLosRolesDeSuAplicacion() {
		when(rolRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(CAJA, CONTABILIDAD, VENTAS));

		assertThat(service.rolesAsignables(ADMINISTRADOR)).extracting(RolVista::nombre).containsExactly("CAJA",
				"CONTABILIDAD");
		assertThat(service.rolesAsignables(GERENTE)).extracting(RolVista::nombre).containsExactly("VENTAS");
	}

	@Test
	void elAdministradorListaSoloUsuariosDelErp() {
		when(usuarioRepository.buscarConAccesoAlErp())
				.thenReturn(List.of(new Usuario("caja1", "hash", "Cajero", CAJA)));

		assertThat(service.listar(ADMINISTRADOR)).extracting(UsuarioVista::usuario).containsExactly("caja1");
		verify(usuarioRepository, never()).buscarConAccesoAlCrm();
	}

	@Test
	void cambiaElRolDeUnUsuarioDeSuAplicacion() {
		Usuario usuario = new Usuario("caja1", "hash", "Cajero", CAJA);
		when(usuarioRepository.buscarPorIdConRol(1L)).thenReturn(Optional.of(usuario));
		when(rolRepository.findByNombre("CONTABILIDAD")).thenReturn(Optional.of(CONTABILIDAD));

		UsuarioVista actualizado = service.cambiarRol(1L, "CONTABILIDAD", ADMINISTRADOR);

		assertThat(actualizado.rol()).isEqualTo("CONTABILIDAD");
	}

	@Test
	void noPuedeCambiarElRolDeUnUsuarioDeOtraAplicacion() {
		when(usuarioRepository.buscarPorIdConRol(2L))
				.thenReturn(Optional.of(new Usuario("vendedor1", "hash", "Vendedor", VENTAS)));

		assertThatThrownBy(() -> service.cambiarRol(2L, "CAJA", ADMINISTRADOR))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}
}
