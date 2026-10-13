package dev.latticesystems.erp_agente360.backend_agencia360.acceso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsultaVendedoresServiceTest {

	private static final Rol VENTAS = new Rol("VENTAS", "Vendedor", false, true);
	private static final Rol GERENTE = new Rol("GERENTE_MARKETING", "Gerente de marketing", false, true);

	@Mock
	private UsuarioRepository usuarioRepository;

	private ConsultaVendedoresService service;

	@BeforeEach
	void setUp() {
		service = new ConsultaVendedoresService(usuarioRepository);
	}

	@Test
	void unVendedorActivoPuedeTenerProspectos() {
		when(usuarioRepository.buscarPorIdConRol(1L)).thenReturn(Optional.of(usuario(VENTAS, true)));

		assertThat(service.esVendedorActivo(1L)).isTrue();
	}

	@Test
	void unVendedorInactivoNo() {
		when(usuarioRepository.buscarPorIdConRol(1L)).thenReturn(Optional.of(usuario(VENTAS, false)));

		assertThat(service.esVendedorActivo(1L)).isFalse();
	}

	@Test
	void otroRolNo() {
		when(usuarioRepository.buscarPorIdConRol(1L)).thenReturn(Optional.of(usuario(GERENTE, true)));

		assertThat(service.esVendedorActivo(1L)).isFalse();
	}

	@Test
	void unUsuarioQueNoExisteNo() {
		when(usuarioRepository.buscarPorIdConRol(1L)).thenReturn(Optional.empty());

		assertThat(service.esVendedorActivo(1L)).isFalse();
		assertThat(service.esVendedorActivo(null)).isFalse();
	}

	private static Usuario usuario(Rol rol, boolean activo) {
		Usuario usuario = new Usuario("persona1", "hash-de-prueba", "Persona", rol);
		ReflectionTestUtils.setField(usuario, "activo", activo);
		return usuario;
	}
}
