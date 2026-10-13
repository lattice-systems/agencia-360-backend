package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultaRefaccionesServiceTest {

	@Mock
	private RefaccionRepository refaccionRepository;

	private ConsultaRefaccionesService service;

	@BeforeEach
	void setUp() {
		service = new ConsultaRefaccionesService(refaccionRepository);
	}

	@Test
	void listaLasRefaccionesPorCodigo() {
		when(refaccionRepository.findAllByOrderByCodigoAsc())
				.thenReturn(List.of(refaccion("BAL-DEL-01"), refaccion("FIL-ACE-01")));

		assertThat(service.listar()).extracting(RefaccionVista::codigo).containsExactly("BAL-DEL-01", "FIL-ACE-01");
	}

	@Test
	void obtieneUnaRefaccionPorId() {
		when(refaccionRepository.findById(1L)).thenReturn(Optional.of(refaccion("FIL-ACE-01")));

		assertThat(service.obtener(1L).codigo()).isEqualTo("FIL-ACE-01");
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() {
		when(refaccionRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe la refacción 99");
	}

	private static Refaccion refaccion(String codigo) {
		return Refaccion.registrar(codigo, "Pieza " + codigo, new BigDecimal("100.00"), new BigDecimal("150.00"), 2);
	}
}
