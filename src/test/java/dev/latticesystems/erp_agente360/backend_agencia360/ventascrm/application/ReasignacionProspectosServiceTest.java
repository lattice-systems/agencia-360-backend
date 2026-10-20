package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Etapa;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReasignacionProspectosServiceTest {

	private static final Instant REGISTRO = Instant.parse("2026-10-12T18:00:00Z");

	private static final UsuarioAutenticado GERENTE = new UsuarioAutenticado(2L, "gerente1",
			Set.of(Role.GERENTE_MARKETING));
	private static final UsuarioAutenticado VENDEDOR = new UsuarioAutenticado(7L, "vendedor1", Set.of(Role.VENTAS));

	@Mock
	private ProspectoRepository prospectoRepository;

	@Mock
	private ConsultaVendedores consultaVendedores;

	private ReasignacionProspectosService service;

	@BeforeEach
	void setUp() {
		service = new ReasignacionProspectosService(prospectoRepository, consultaVendedores);
	}

	@Test
	void elGerenteReasignaElProspectoAOtroVendedorSinCambiarSuEtapa() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, 7L, REGISTRO);
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(prospecto));
		when(consultaVendedores.esVendedorActivo(8L)).thenReturn(true);

		ProspectoVista vista = service.reasignar(1L, 8L, GERENTE);

		assertThat(vista.vendedorId()).isEqualTo(8L);
		assertThat(vista.etapa()).isEqualTo(Etapa.PRIMER_CONTACTO);
		assertThat(prospecto.getHistorial()).hasSize(1);
	}

	@Test
	void elGerenteReasignaVariosALaVezAlMismoVendedor() {
		Prospecto ana = conId(1L, "Ana López");
		Prospecto luis = conId(2L, "Luis Pérez");
		when(prospectoRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(ana, luis));
		when(consultaVendedores.esVendedorActivo(8L)).thenReturn(true);

		List<ProspectoVista> vistas = service.reasignarVarios(List.of(1L, 2L), 8L, GERENTE);

		assertThat(vistas).extracting(ProspectoVista::vendedorId).containsExactly(8L, 8L);
		assertThat(ana.getVendedorId()).isEqualTo(8L);
		assertThat(luis.getVendedorId()).isEqualTo(8L);
	}

	@Test
	void siUnoNoExisteNoSeReasignaNinguno() {
		Prospecto ana = conId(1L, "Ana López");
		when(prospectoRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(ana));

		assertThatThrownBy(() -> service.reasignarVarios(List.of(1L, 99L), 8L, GERENTE))
				.isInstanceOf(RecursoNoEncontradoException.class).hasMessage("No existe el prospecto 99");
		assertThat(ana.getVendedorId()).isEqualTo(7L);
	}

	@Test
	void pideAlMenosUnProspectoYHasta100() {
		assertThatThrownBy(() -> service.reasignarVarios(List.of(), 8L, GERENTE))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Elige de 1 a 100 prospectos");
		List<Long> muchos = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
		assertThatThrownBy(() -> service.reasignarVarios(muchos, 8L, GERENTE))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(prospectoRepository);
	}

	@Test
	void soloElGerenteReasignaVarios() {
		assertThatThrownBy(() -> service.reasignarVarios(List.of(1L), 8L, VENDEDOR))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository, consultaVendedores);
	}

	@Test
	void reasignarAlMismoVendedorNoCambiaNada() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, 7L, REGISTRO);

		assertThat(prospecto.reasignarA(7L)).isFalse();
		assertThat(prospecto.getVendedorId()).isEqualTo(7L);
	}

	@Test
	void soloElGerenteDeMarketingReasigna() {
		assertThatThrownBy(() -> service.reasignar(1L, 8L, VENDEDOR)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(prospectoRepository, consultaVendedores);
	}

	@Test
	void respondeNoEncontradoSiElProspectoNoExiste() {
		when(prospectoRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reasignar(99L, 8L, GERENTE)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el prospecto 99");
	}

	@Test
	void rechazaUnUsuarioQueNoEsVendedorActivo() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, 7L, REGISTRO);
		when(prospectoRepository.findById(1L)).thenReturn(Optional.of(prospecto));
		when(consultaVendedores.esVendedorActivo(3L)).thenReturn(false);

		assertThatThrownBy(() -> service.reasignar(1L, 3L, GERENTE)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("El usuario 3 no es un vendedor activo");
		assertThat(prospecto.getVendedorId()).isEqualTo(7L);
	}

	private static Prospecto conId(Long id, String nombre) {
		Prospecto prospecto = Prospecto.registrar(nombre, "477123456" + id, null, 7L, REGISTRO);
		ReflectionTestUtils.setField(prospecto, "id", id);
		return prospecto;
	}
}
