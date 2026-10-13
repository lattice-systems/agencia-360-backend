package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.OrigenCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class SeguimientoProspectosServiceTest {

	private static final Instant REGISTRO = Instant.parse("2026-10-12T18:00:00Z");
	private static final Instant AHORA = Instant.parse("2026-10-13T10:30:00Z");

	private static final UsuarioAutenticado VENDEDOR = new UsuarioAutenticado(7L, "vendedor1", Set.of(Role.VENTAS));
	private static final UsuarioAutenticado GERENTE = new UsuarioAutenticado(2L, "gerente1",
			Set.of(Role.GERENTE_MARKETING));
	private static final UsuarioAutenticado CONTABILIDAD = new UsuarioAutenticado(3L, "conta1",
			Set.of(Role.CONTABILIDAD));

	@Mock
	private ProspectoRepository prospectoRepository;

	private SeguimientoProspectosService service;

	@BeforeEach
	void setUp() {
		service = new SeguimientoProspectosService(prospectoRepository, Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	void unVendedorSoloRecibeSusProspectos() {
		when(prospectoRepository.findByVendedorIdOrderByCreatedAtDescIdDesc(7L))
				.thenReturn(List.of(prospecto("Ana López", 7L)));

		List<ProspectoVista> lista = service.listar(null, VENDEDOR);

		assertThat(lista).extracting(ProspectoVista::nombre).containsExactly("Ana López");
	}

	@Test
	void unVendedorFiltraSusProspectosPorEtapa() {
		when(prospectoRepository.findByVendedorIdAndEtapaOrderByCreatedAtDescIdDesc(7L, Etapa.VISITA))
				.thenReturn(List.of());

		assertThat(service.listar(Etapa.VISITA, VENDEDOR)).isEmpty();
	}

	@Test
	void elGerenteDeMarketingRecibeTodos() {
		when(prospectoRepository.findAllByOrderByCreatedAtDescIdDesc())
				.thenReturn(List.of(prospecto("Ana López", 7L), prospecto("Luis Pérez", 8L)));

		assertThat(service.listar(null, GERENTE)).hasSize(2);
	}

	@Test
	void elGerenteDeMarketingFiltraTodosPorEtapa() {
		when(prospectoRepository.findByEtapaOrderByCreatedAtDescIdDesc(Etapa.PRIMER_CONTACTO))
				.thenReturn(List.of(prospecto("Ana López", 7L)));

		assertThat(service.listar(Etapa.PRIMER_CONTACTO, GERENTE)).hasSize(1);
	}

	@Test
	void unRolSinFuncionEnElCrmNoVeProspectos() {
		assertThatThrownBy(() -> service.listar(null, CONTABILIDAD)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository);
	}

	@Test
	void unVendedorCambiaLaEtapaDeSuProspecto() {
		Prospecto ana = prospecto("Ana López", 7L);
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.of(ana));

		ProspectoVista actualizado = service.cambiarEtapa(1L, Etapa.VISITA, null, VENDEDOR);

		assertThat(actualizado.etapa()).isEqualTo(Etapa.VISITA);
		assertThat(ana.getHistorial()).last().satisfies(movimiento -> {
			assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.CRM);
			assertThat(movimiento.getFechaHora()).isEqualTo(AHORA);
		});
	}

	@Test
	void unProspectoAjenoSeTrataComoInexistente() {
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.cambiarEtapa(1L, Etapa.VISITA, null, VENDEDOR))
				.isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe el prospecto 1");
	}

	@Test
	void unVendedorCierraSuProspectoConMotivo() {
		Prospecto ana = prospecto("Ana López", 7L);
		when(prospectoRepository.findByIdAndVendedorId(1L, 7L)).thenReturn(Optional.of(ana));

		ProspectoVista cerrado = service.cambiarEtapa(1L, Etapa.CERRADO, "Compró en otra agencia", VENDEDOR);

		assertThat(cerrado.etapa()).isEqualTo(Etapa.CERRADO);
		assertThat(cerrado.motivoCierre()).isEqualTo("Compró en otra agencia");
		assertThat(cerrado.ultimaEtapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
	}

	@Test
	void elGerenteDeMarketingNoCambiaEtapasAunqueVeaTodo() {
		assertThatThrownBy(() -> service.cambiarEtapa(1L, Etapa.VISITA, null, GERENTE))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository);
	}

	private static Prospecto prospecto(String nombre, Long vendedorId) {
		return Prospecto.registrar(nombre, "47700000%02d".formatted(vendedorId), null, vendedorId, REGISTRO);
	}
}
