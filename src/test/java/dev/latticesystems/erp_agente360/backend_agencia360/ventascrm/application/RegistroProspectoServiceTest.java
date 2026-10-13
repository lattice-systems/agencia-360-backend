package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistroProspectoServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-12T18:00:00Z");
	private static final UsuarioAutenticado VENDEDOR = new UsuarioAutenticado(7L, "vendedor1", Set.of(Role.VENTAS));

	@Mock
	private ProspectoRepository prospectoRepository;

	private RegistroProspectoService service;

	@BeforeEach
	void setUp() {
		service = new RegistroProspectoService(prospectoRepository, Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void registraAlProspectoAsignadoAlVendedorEnSesion() {
		when(prospectoRepository.save(any(Prospecto.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProspectoVista creado = service.registrar(new NuevoProspecto("Ana López", "4771234567", null), VENDEDOR);

		assertThat(creado.nombre()).isEqualTo("Ana López");
		assertThat(creado.etapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
		assertThat(creado.vendedorId()).isEqualTo(7L);
		ArgumentCaptor<Prospecto> guardado = ArgumentCaptor.forClass(Prospecto.class);
		verify(prospectoRepository).save(guardado.capture());
		assertThat(guardado.getValue().getHistorial()).singleElement()
				.satisfies(movimiento -> assertThat(movimiento.getFechaHora()).isEqualTo(AHORA));
	}

	@Test
	void rechazaUnTelefonoQueYaExiste() {
		when(prospectoRepository.existsByTelefono("4771234567")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(new NuevoProspecto("Ana López", "4771234567", null), VENDEDOR))
				.isInstanceOf(ConflictoException.class).hasMessage("Ya existe un prospecto con ese teléfono o correo");
		verify(prospectoRepository, never()).save(any());
	}

	@Test
	void rechazaUnCorreoQueYaExisteSinImportarMayusculas() {
		when(prospectoRepository.existsByCorreo("ana@ejemplo.com")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(new NuevoProspecto("Ana López", null, "ANA@ejemplo.com"), VENDEDOR))
				.isInstanceOf(ConflictoException.class);
		verify(prospectoRepository, never()).save(any());
	}

	@Test
	void noBuscaDuplicadosDeUnDatoQueNoSeCapturo() {
		when(prospectoRepository.save(any(Prospecto.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.registrar(new NuevoProspecto("Ana López", "4771234567", null), VENDEDOR);

		verify(prospectoRepository, never()).existsByCorreo(any());
	}

	@Test
	void rechazaDatosIncompletosAntesDeConsultarLaBase() {
		assertThatThrownBy(() -> service.registrar(new NuevoProspecto("Ana López", null, null), VENDEDOR))
				.isInstanceOf(IllegalArgumentException.class);
		verify(prospectoRepository, never()).existsByTelefono(any());
		verify(prospectoRepository, never()).save(any());
	}
}
