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
	private static final Instant AYER = Instant.parse("2026-10-12T15:30:00Z");

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

		MovimientoInventario primera = refaccion
				.recibir(new Recepcion(10, "Refacciones del Bajío", "Caja golpeada", AYER), RESPONSABLE, AHORA);
		refaccion.recibir(recepcion(3), RESPONSABLE, AHORA);

		assertThat(refaccion.getExistencia()).isEqualTo(13);
		assertThat(primera.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
		assertThat(primera.getRefaccion()).isSameAs(refaccion);
		assertThat(primera.getVehiculo()).isNull();
		assertThat(primera.getCantidad()).isEqualTo(10);
		assertThat(primera.getProveedor()).isEqualTo("Refacciones del Bajío");
		assertThat(primera.getNotas()).isEqualTo("Caja golpeada");
		assertThat(primera.getResponsableId()).isEqualTo(RESPONSABLE);
		assertThat(primera.getFechaLlegada()).isEqualTo(AYER);
		assertThat(primera.getFechaRegistro()).isEqualTo(AHORA);
	}

	@Test
	void sinFechaDeLlegadaSeTomaLaDelRegistro() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		MovimientoInventario movimiento = refaccion.recibir(new Recepcion(2, " Bajío ", "  ", null), RESPONSABLE,
				AHORA);

		assertThat(movimiento.getFechaLlegada()).isEqualTo(AHORA);
		assertThat(movimiento.getProveedor()).isEqualTo("Bajío");
		assertThat(movimiento.getNotas()).isNull();
	}

	@Test
	void rechazaUnaLlegadaPosteriorAlRegistro() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		assertThatThrownBy(
				() -> refaccion.recibir(new Recepcion(2, "Bajío", null, AHORA.plusSeconds(60)), RESPONSABLE, AHORA))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La fecha de llegada no puede ser posterior al registro");
		assertThat(refaccion.getExistencia()).isZero();
	}

	@Test
	void rechazaUnaRecepcionSinProveedorOConTextosDemasiadoLargos() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		assertThatThrownBy(() -> refaccion.recibir(new Recepcion(2, " ", null, null), RESPONSABLE, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El proveedor es obligatorio");
		assertThatThrownBy(() -> refaccion.recibir(new Recepcion(2, "x".repeat(121), null, null), RESPONSABLE, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El proveedor admite hasta 120 caracteres");
		assertThatThrownBy(
				() -> refaccion.recibir(new Recepcion(2, "Bajío", "x".repeat(501), null), RESPONSABLE, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Las notas admiten hasta 500 caracteres");
		assertThat(refaccion.getExistencia()).isZero();
	}

	@Test
	void rechazaRecibirUnaCantidadQueNoEsMayorACero() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		assertThatThrownBy(() -> refaccion.recibir(recepcion(0), RESPONSABLE, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La cantidad debe ser mayor a cero");
		assertThat(refaccion.getExistencia()).isZero();
	}

	@Test
	void rechazaUnaRecepcionQueRebasaLaExistenciaMaxima() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);
		refaccion.recibir(recepcion(Integer.MAX_VALUE), RESPONSABLE, AHORA);

		assertThatThrownBy(() -> refaccion.recibir(recepcion(1), RESPONSABLE, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La existencia rebasaría el máximo permitido");
		assertThat(refaccion.getExistencia()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void seEditanSusDatosSinTocarLaExistencia() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);
		refaccion.recibir(recepcion(7), RESPONSABLE, AHORA);

		refaccion.editar(" fil-ace-02 ", " Filtro de aceite sintético ", new BigDecimal("190.00"),
				new BigDecimal("350.00"), 3);

		assertThat(refaccion.getCodigo()).isEqualTo("FIL-ACE-02");
		assertThat(refaccion.getDescripcion()).isEqualTo("Filtro de aceite sintético");
		assertThat(refaccion.getCosto()).isEqualByComparingTo("190.00");
		assertThat(refaccion.getPrecio()).isEqualByComparingTo("350.00");
		assertThat(refaccion.getMinimo()).isEqualTo(3);
		assertThat(refaccion.getExistencia()).isEqualTo(7);
	}

	@Test
	void unaEdicionInvalidaNoCambiaNada() {
		Refaccion refaccion = Refaccion.registrar("FIL-ACE-01", "Filtro de aceite", COSTO, PRECIO, 5);

		assertThatThrownBy(() -> refaccion.editar("FIL-ACE-02", "Otra", COSTO, BigDecimal.ZERO, 5))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio debe ser mayor a cero");
		assertThat(refaccion.getCodigo()).isEqualTo("FIL-ACE-01");
		assertThat(refaccion.getDescripcion()).isEqualTo("Filtro de aceite");
	}

	private static Recepcion recepcion(int cantidad) {
		return new Recepcion(cantidad, "Refacciones del Bajío", null, null);
	}
}
