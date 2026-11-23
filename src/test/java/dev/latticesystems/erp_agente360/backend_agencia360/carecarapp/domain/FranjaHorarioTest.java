package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class FranjaHorarioTest {

	private static FranjaHorario lunesALasNueve() {
		return FranjaHorario.nueva(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), 3);
	}

	@Test
	void naceActivaConSuDiaHorasYCapacidad() {
		FranjaHorario franja = lunesALasNueve();

		assertThat(franja.getDiaSemana()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(franja.getHoraInicio()).isEqualTo(LocalTime.of(9, 0));
		assertThat(franja.getHoraFin()).isEqualTo(LocalTime.of(10, 0));
		assertThat(franja.getCapacidad()).isEqualTo(3);
		assertThat(franja.isActiva()).isTrue();
	}

	@Test
	void soloEsDeLasFechasDeSuDia() {
		FranjaHorario franja = lunesALasNueve();

		assertThat(franja.esDe(LocalDate.of(2026, 11, 23))).isTrue();
		assertThat(franja.esDe(LocalDate.of(2026, 11, 24))).isFalse();
		assertThat(franja.esDe(null)).isFalse();
	}

	@Test
	void rechazaDatosFaltantesOHorasAlReves() {
		assertThatThrownBy(() -> FranjaHorario.nueva(null, LocalTime.of(9, 0), LocalTime.of(10, 0), 3))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorios");
		assertThatThrownBy(() -> FranjaHorario.nueva(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(9, 0), 3))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posterior");
		assertThatThrownBy(() -> FranjaHorario.nueva(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 0), 3))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posterior");
	}

	@Test
	void laCapacidadSiempreEsMayorACero() {
		assertThatThrownBy(() -> FranjaHorario.nueva(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), 0))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mayor a cero");

		FranjaHorario franja = lunesALasNueve();
		franja.cambiarCapacidad(5);
		assertThat(franja.getCapacidad()).isEqualTo(5);
		assertThatThrownBy(() -> franja.cambiarCapacidad(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThat(franja.getCapacidad()).isEqualTo(5);
	}

	@Test
	void unaFranjaDesactivadaDejaDeEstarActiva() {
		FranjaHorario franja = lunesALasNueve();
		franja.desactivar();

		assertThat(franja.isActiva()).isFalse();
	}
}
