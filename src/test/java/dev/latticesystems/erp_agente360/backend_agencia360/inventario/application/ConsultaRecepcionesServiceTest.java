package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Recepcion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultaRecepcionesServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");
	private static final Instant AYER = Instant.parse("2026-10-12T15:30:00Z");

	@Mock
	private RefaccionRepository refaccionRepository;

	@Mock
	private MovimientoInventarioRepository movimientoRepository;

	private ConsultaRecepcionesService service;

	@BeforeEach
	void setUp() {
		service = new ConsultaRecepcionesService(refaccionRepository, movimientoRepository);
	}

	@Test
	void listaLasRecepcionesConSusDosFechas() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", new BigDecimal("180.50"),
				new BigDecimal("320.00"), 5);
		when(refaccionRepository.existsById(1L)).thenReturn(true);
		when(movimientoRepository.findByRefaccionIdAndTipoOrderByFechaRegistroDescIdDesc(1L, TipoMovimiento.ENTRADA))
				.thenReturn(List.of(refaccion.recibir(new Recepcion(4, "Bajío", "Tarde", AYER), 7L, AHORA)));

		assertThat(service.listar(1L)).singleElement().satisfies(recepcion -> {
			assertThat(recepcion.cantidad()).isEqualTo(4);
			assertThat(recepcion.proveedor()).isEqualTo("Bajío");
			assertThat(recepcion.notas()).isEqualTo("Tarde");
			assertThat(recepcion.fechaLlegada()).isEqualTo(AYER);
			assertThat(recepcion.fechaRegistro()).isEqualTo(AHORA);
			assertThat(recepcion.responsableId()).isEqualTo(7L);
		});
	}

	@Test
	void unaRefaccionQueNoExisteResponde404() {
		when(refaccionRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> service.listar(99L)).isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe la refacción 99");
	}
}
