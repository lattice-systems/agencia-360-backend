package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistroRefaccionServiceTest {

	@Mock
	private RefaccionRepository refaccionRepository;

	private RegistroRefaccionService service;

	@BeforeEach
	void setUp() {
		service = new RegistroRefaccionService(refaccionRepository);
	}

	@Test
	void registraLaRefaccionConExistenciaEnCero() {
		when(refaccionRepository.save(any(Refaccion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		RefaccionVista vista = service.registrar(nueva(" fil-ace-01 "));

		assertThat(vista.codigo()).isEqualTo("FIL-ACE-01");
		assertThat(vista.descripcion()).isEqualTo("Filtro de aceite");
		assertThat(vista.costo()).isEqualByComparingTo("180.50");
		assertThat(vista.precio()).isEqualByComparingTo("320.00");
		assertThat(vista.existencia()).isZero();
		assertThat(vista.minimo()).isEqualTo(5);
	}

	@Test
	void rechazaUnCodigoQueYaExiste() {
		when(refaccionRepository.existsByCodigo("FIL-ACE-01")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(nueva("fil-ace-01"))).isInstanceOf(ConflictoException.class)
				.hasMessage("Ya existe una refacción con el código FIL-ACE-01");
		verify(refaccionRepository, never()).save(any());
	}

	private static NuevaRefaccion nueva(String codigo) {
		return new NuevaRefaccion(codigo, "Filtro de aceite", new BigDecimal("180.50"), new BigDecimal("320.00"), 5);
	}
}
