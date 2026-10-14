package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.InOrder;
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
		guardarSinCambios();

		ProspectoRegistrado resultado = service.registrar(new NuevoProspecto("Ana López", "4771234567", null),
				VENDEDOR);

		assertThat(resultado.prospecto().nombre()).isEqualTo("Ana López");
		assertThat(resultado.prospecto().etapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
		assertThat(resultado.prospecto().vendedorId()).isEqualTo(7L);
		assertThat(resultado.contactoRepetido()).isFalse();
		ArgumentCaptor<Prospecto> guardado = ArgumentCaptor.forClass(Prospecto.class);
		verify(prospectoRepository).save(guardado.capture());
		assertThat(guardado.getValue().getHistorial()).singleElement()
				.satisfies(movimiento -> assertThat(movimiento.getFechaHora()).isEqualTo(AHORA));
	}

	@Test
	void registraAunqueElTelefonoYaExistaYLoAvisa() {
		when(prospectoRepository.existsByTelefono("4771234567")).thenReturn(true);
		guardarSinCambios();

		ProspectoRegistrado resultado = service.registrar(new NuevoProspecto("Ana López", "4771234567", null),
				VENDEDOR);

		assertThat(resultado.contactoRepetido()).isTrue();
		verify(prospectoRepository).save(any(Prospecto.class));
	}

	@Test
	void registraAunqueElCorreoYaExistaSinImportarMayusculasYLoAvisa() {
		when(prospectoRepository.existsByCorreo("ana@ejemplo.com")).thenReturn(true);
		guardarSinCambios();

		ProspectoRegistrado resultado = service.registrar(new NuevoProspecto("Ana López", null, "ANA@ejemplo.com"),
				VENDEDOR);

		assertThat(resultado.contactoRepetido()).isTrue();
	}

	@Test
	void buscaRepetidosAntesDeGuardarParaNoEncontrarseASiMismo() {
		guardarSinCambios();

		service.registrar(new NuevoProspecto("Ana López", "4771234567", null), VENDEDOR);

		InOrder orden = inOrder(prospectoRepository);
		orden.verify(prospectoRepository).existsByTelefono("4771234567");
		orden.verify(prospectoRepository).save(any(Prospecto.class));
	}

	@Test
	void noBuscaRepetidosDeUnDatoQueNoSeCapturo() {
		guardarSinCambios();

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

	@Test
	void revisaCoincidenciasMientrasSeCapturaConElTelefonoNormalizado() {
		when(prospectoRepository.existsByTelefono("4771234567")).thenReturn(true);

		assertThat(service.hayContactoRepetido("477 123 45 67", null)).isTrue();
	}

	@Test
	void unTelefonoIncompletoTodaviaNoSeCompara() {
		assertThat(service.hayContactoRepetido("477", null)).isFalse();
		verify(prospectoRepository, never()).existsByTelefono(any());
	}

	@Test
	void revisaCoincidenciasDelCorreoEnMinusculas() {
		when(prospectoRepository.existsByCorreo("ana@ejemplo.com")).thenReturn(true);

		assertThat(service.hayContactoRepetido(null, " Ana@Ejemplo.com ")).isTrue();
	}

	private void guardarSinCambios() {
		when(prospectoRepository.save(any(Prospecto.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}
}
