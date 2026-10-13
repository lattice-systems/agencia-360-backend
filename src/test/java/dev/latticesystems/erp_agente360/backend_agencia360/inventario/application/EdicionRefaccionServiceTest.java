package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Recepcion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EdicionRefaccionServiceTest {

	private static final RefaccionEditada CORREGIDA = new RefaccionEditada("fil-ace-02", "Filtro sintético",
			new BigDecimal("190.00"), new BigDecimal("350.00"), 3);

	@Mock
	private RefaccionRepository refaccionRepository;

	private EdicionRefaccionService service;

	@BeforeEach
	void setUp() {
		service = new EdicionRefaccionService(refaccionRepository);
	}

	@Test
	void corrigeLosDatosSinTocarLaExistencia() {
		Refaccion refaccion = refaccionConExistencia(7);
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccion));

		RefaccionVista vista = service.editar(1L, CORREGIDA);

		assertThat(vista.codigo()).isEqualTo("FIL-ACE-02");
		assertThat(vista.descripcion()).isEqualTo("Filtro sintético");
		assertThat(vista.minimo()).isEqualTo(3);
		assertThat(vista.existencia()).isEqualTo(7);
	}

	@Test
	void bloqueaLaFilaComoLaRecepcion() {
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccionConExistencia(0)));

		service.editar(1L, CORREGIDA);

		verify(refaccionRepository).buscarParaActualizar(1L);
		verify(refaccionRepository, never()).findById(anyLong());
	}

	@Test
	void rechazaUnCodigoQueYaTieneOtraRefaccionSinTocarLaRefaccion() {
		Refaccion refaccion = refaccionConExistencia(0);
		when(refaccionRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(refaccion));
		when(refaccionRepository.existsByCodigoAndIdNot("FIL-ACE-02", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.editar(1L, CORREGIDA)).isInstanceOf(ConflictoException.class)
				.hasMessage("Ya existe una refacción con el código FIL-ACE-02");
		assertThat(refaccion.getCodigo()).isEqualTo("FIL-ACE-01");
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() {
		when(refaccionRepository.buscarParaActualizar(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.editar(99L, CORREGIDA)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe la refacción 99");
	}

	private static Refaccion refaccionConExistencia(int existencia) {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", new BigDecimal("180.50"),
				new BigDecimal("320.00"), 5);
		if (existencia > 0) {
			refaccion.recibir(new Recepcion(existencia, "Bajío", null, null), 1L, Instant.now());
		}
		return refaccion;
	}
}
