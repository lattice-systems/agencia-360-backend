package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefaccionTest {

	private static final BigDecimal COSTO = new BigDecimal("180.50");
	private static final BigDecimal PRECIO = new BigDecimal("320.00");
	private static final Long RESPONSABLE = 4L;
	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");

	@Test
	void seRegistraSinExistencia() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		assertThat(refaccion.getCodigo()).isEqualTo("FIL-ACE-01");
		assertThat(refaccion.getDescripcion()).isEqualTo("Filtro de aceite");
		assertThat(refaccion.getCosto()).isEqualByComparingTo(COSTO);
		assertThat(refaccion.getPrecio()).isEqualByComparingTo(PRECIO);
		assertThat(refaccion.getMinimo()).isEqualTo(5);
		assertThat(refaccion.getExistencia()).isZero();
	}

	@Test
	void quitaEspaciosYGuardaElCodigoEnMayusculas() {
		Refaccion refaccion = Refaccion.registrar(" fil-ace-01 ", " Filtro de aceite  ", COSTO, PRECIO, 5);

		assertThat(refaccion.getCodigo()).isEqualTo("FIL-ACE-01");
		assertThat(refaccion.getDescripcion()).isEqualTo("Filtro de aceite");
	}

	@Test
	void rechazaCodigoODescripcionVacios() {
		assertThatThrownBy(() -> Refaccion.registrar(" ", "Filtro de aceite", COSTO, PRECIO, 5))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El código es obligatorio");
		assertThatThrownBy(() -> Refaccion.registrar("FIL-ACE-01", null, COSTO, PRECIO, 5))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La descripción es obligatoria");
	}

	@Test
	void rechazaMontosOMinimoInvalidos() {
		assertThatThrownBy(() -> Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", new BigDecimal("-1"), PRECIO, 5))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El costo no puede ser negativo");
		assertThatThrownBy(() -> Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, BigDecimal.ZERO, 5))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio debe ser mayor a cero");
		assertThatThrownBy(() -> Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, -1))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El mínimo no puede ser negativo");
	}

	@Test
	void recibirSumaALaExistenciaYDevuelveElMovimientoDeEntrada() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		MovimientoInventario primera = refaccion.recibir(10, RESPONSABLE, AHORA);
		refaccion.recibir(3, RESPONSABLE, AHORA);

		assertThat(refaccion.getExistencia()).isEqualTo(13);
		assertThat(primera.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(primera.getRefaccion()).isSameAs(refaccion);
		assertThat(primera.getVehiculo()).isNull();
		assertThat(primera.getCantidad()).isEqualTo(10);
		assertThat(primera.getResponsableId()).isEqualTo(RESPONSABLE);
		assertThat(primera.getFecha()).isEqualTo(AHORA);
	}

	@Test
	void rechazaRecibirUnaCantidadQueNoEsMayorACero() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		assertThatThrownBy(() -> refaccion.recibir(0, RESPONSABLE, AHORA)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La cantidad debe ser mayor a cero");
		assertThat(refaccion.getExistencia()).isZero();
	}

	@Test
	void rechazaUnaRecepcionQueRebasaLaExistenciaMaxima() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);
		refaccion.recibir(Integer.MAX_VALUE, RESPONSABLE, AHORA);

		assertThatThrownBy(() -> refaccion.recibir(1, RESPONSABLE, AHORA)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La existencia rebasaría el máximo permitido");
		assertThat(refaccion.getExistencia()).isEqualTo(Integer.MAX_VALUE);
	}
}
