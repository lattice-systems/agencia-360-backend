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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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
}
