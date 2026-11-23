package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdenServicioTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 26);
	private static final ZonedDateTime AHORA = HOY.atTime(13, 0).atZone(ZoneId.of("America/Mexico_City"));

	private static RenglonNuevo renglon(TipoRenglon tipo, String concepto, int cantidad, String precio) {
		return new RenglonNuevo(tipo, concepto, cantidad, new BigDecimal(precio));
	}

	@Test
	void sumaLosRenglonesYDesglosaElIvaIncluido() {
		OrdenServicio orden = OrdenServicio.registrar(3L, HOY, 15_000, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, " Cambio de aceite ", 1, "450.00"),
						renglon(TipoRenglon.REFACCION, "Filtro de aceite", 2, "175.50")),
				AHORA);

		assertThat(orden.getRenglones()).extracting(RenglonOrden::getConcepto).containsExactly("Cambio de aceite",
				"Filtro de aceite");
		assertThat(orden.getRenglones().get(1).getImporte()).isEqualByComparingTo("351.00");
		assertThat(orden.getTotal()).isEqualByComparingTo("801.00");
		assertThat(orden.getSubtotal()).isEqualByComparingTo("690.52");
		assertThat(orden.getIva()).isEqualByComparingTo("110.48");
		assertThat(orden.getSubtotal().add(orden.getIva())).isEqualByComparingTo(orden.getTotal());
		assertThat(orden.getKilometraje()).isEqualTo(15_000);
		assertThat(orden.getAtendidoPor()).isEqualTo(7L);
	}

	@Test
	void elTotalEsLaManoDeObraMasLasRefacciones() {
		OrdenServicio orden = OrdenServicio.registrar(3L, HOY, 15_000, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Filtro de aceite", 2, "175.50"),
						renglon(TipoRenglon.MANO_DE_OBRA, "Cambio de aceite", 1, "450.00"),
						renglon(TipoRenglon.REFACCION, "Aceite sintético", 4, "120.25"),
						renglon(TipoRenglon.MANO_DE_OBRA, "Revisión de frenos", 1, "300.00")),
				AHORA);

		assertThat(orden.getManoDeObra()).isEqualByComparingTo("750.00");
		assertThat(orden.getRefacciones()).isEqualByComparingTo("832.00");
		assertThat(orden.getTotal()).isEqualByComparingTo(orden.getManoDeObra().add(orden.getRefacciones()));
		assertThat(orden.getRenglones()).extracting(RenglonOrden::getTipo).containsExactly(TipoRenglon.MANO_DE_OBRA,
				TipoRenglon.MANO_DE_OBRA, TipoRenglon.REFACCION, TipoRenglon.REFACCION);
		assertThat(orden.getRenglones()).extracting(RenglonOrden::getConcepto).containsExactly("Cambio de aceite",
				"Revisión de frenos", "Filtro de aceite", "Aceite sintético");
		assertThat(orden.getRenglones().get(2)).isInstanceOf(OrdenRefaccion.class);
		assertThat(((OrdenRefaccion) orden.getRenglones().get(2)).getRefaccionId()).isNull();
	}

	@Test
	void unaOrdenSoloDeRefaccionesNoLlevaManoDeObra() {
		OrdenServicio orden = OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Filtro de aire", 1, "290.00")), AHORA);

		assertThat(orden.getManoDeObra()).isEqualByComparingTo("0.00");
		assertThat(orden.getRefacciones()).isEqualByComparingTo("290.00");
		assertThat(orden.getTotal()).isEqualByComparingTo("290.00");
	}

	@Test
	void unServicioRegistradoQuedaTerminadoYCerradoEnEseMomento() {
		OrdenServicio orden = OrdenServicio.registrar(3L, HOY.minusDays(3), 100, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, "900")), AHORA);

		assertThat(orden.getEstatus()).isEqualTo(EstatusOrden.TERMINADO);
		assertThat(orden.getEstatus().cerrada()).isTrue();
		assertThat(orden.getFechaCierre()).isEqualTo(AHORA.toInstant());
	}

	@Test
	void unServicioDeCortesiaPuedeSalirEnCero() {
		OrdenServicio orden = OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Revisión de garantía", 1, "0")), AHORA);

		assertThat(orden.getTotal()).isEqualByComparingTo("0.00");
		assertThat(orden.getIva()).isEqualByComparingTo("0.00");
	}

	@Test
	void laFechaNoPuedeSerPosteriorAHoy() {
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY.plusDays(1), 100, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, "900")), AHORA))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La fecha del servicio no puede ser posterior a hoy");
	}

	@Test
	void exigeAlMenosUnRenglon() {
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, 100, 7L, Collections.emptyList(), AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El servicio debe tener al menos un renglón");
	}

	@Test
	void rechazaUnKilometrajeNegativo() {
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, -1, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, "900")), AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("El kilometraje debe estar");
	}

	@Test
	void rechazaRenglonesNoValidos() {
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "  ", 1, "10")), AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("El concepto es obligatorio");
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Balatas", 0, "10")), AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La cantidad debe estar entre 1 y 999");
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Balatas", 1, "-5")), AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("El precio debe estar");
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Balatas", 1, "10.005")), AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio admite hasta dos decimales");
		assertThatThrownBy(() -> OrdenServicio.registrar(3L, HOY, 100, 7L,
				List.of(new RenglonNuevo(null, "Balatas", 1, BigDecimal.TEN)), AHORA))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Cada renglón dice si es mano de obra o refacción");
	}
}
