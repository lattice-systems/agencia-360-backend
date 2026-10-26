package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServicioTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 26);

	private static RenglonNuevo renglon(TipoRenglon tipo, String concepto, int cantidad, String precio) {
		return new RenglonNuevo(tipo, concepto, cantidad, new BigDecimal(precio));
	}

	@Test
	void sumaLosRenglonesYDesglosaElIvaIncluido() {
		Servicio servicio = Servicio.registrar(3L, HOY, 15_000, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, " Cambio de aceite ", 1, "450.00"),
						renglon(TipoRenglon.REFACCION, "Filtro de aceite", 2, "175.50")),
				HOY);

		assertThat(servicio.getRenglones()).extracting(RenglonServicio::getConcepto).containsExactly("Cambio de aceite",
				"Filtro de aceite");
		assertThat(servicio.getRenglones().get(1).getImporte()).isEqualByComparingTo("351.00");
		assertThat(servicio.getTotal()).isEqualByComparingTo("801.00");
		assertThat(servicio.getSubtotal()).isEqualByComparingTo("690.52");
		assertThat(servicio.getIva()).isEqualByComparingTo("110.48");
		assertThat(servicio.getSubtotal().add(servicio.getIva())).isEqualByComparingTo(servicio.getTotal());
		assertThat(servicio.getKilometraje()).isEqualTo(15_000);
		assertThat(servicio.getAtendidoPor()).isEqualTo(7L);
	}

	@Test
	void unServicioDeCortesiaPuedeSalirEnCero() {
		Servicio servicio = Servicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Revisión de garantía", 1, "0")), HOY);

		assertThat(servicio.getTotal()).isEqualByComparingTo("0.00");
		assertThat(servicio.getIva()).isEqualByComparingTo("0.00");
	}

	@Test
	void laFechaNoPuedeSerPosteriorAHoy() {
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY.plusDays(1), 100, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, "900")), HOY))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("La fecha del servicio no puede ser posterior a hoy");
	}

	@Test
	void exigeAlMenosUnRenglon() {
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY, 100, 7L, Collections.emptyList(), HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El servicio debe tener al menos un renglón");
	}

	@Test
	void rechazaUnKilometrajeNegativo() {
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY, -1, 7L,
				List.of(renglon(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, "900")), HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("El kilometraje debe estar");
	}

	@Test
	void rechazaRenglonesNoValidos() {
		assertThatThrownBy(
				() -> Servicio.registrar(3L, HOY, 100, 7L, List.of(renglon(TipoRenglon.REFACCION, "  ", 1, "10")), HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("El concepto es obligatorio");
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Balatas", 0, "10")), HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La cantidad debe estar entre 1 y 999");
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Balatas", 1, "-5")), HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("El precio debe estar");
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY, 100, 7L,
				List.of(renglon(TipoRenglon.REFACCION, "Balatas", 1, "10.005")), HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("El precio admite hasta dos decimales");
		assertThatThrownBy(() -> Servicio.registrar(3L, HOY, 100, 7L,
				List.of(new RenglonNuevo(null, "Balatas", 1, BigDecimal.TEN)), HOY))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Cada renglón dice si es mano de obra o refacción");
	}
}
