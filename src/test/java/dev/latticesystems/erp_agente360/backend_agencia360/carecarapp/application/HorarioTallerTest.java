package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HorarioTallerTest {

	private static HorarioTaller horario(LocalTime apertura, LocalTime cierre, int minutos) {
		return new HorarioTaller(List.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), apertura, cierre, minutos, 3, 24, 60);
	}

	@Test
	void parteElDiaEnFranjasNumeradasDesdeUno() {
		HorarioTaller horario = horario(LocalTime.of(9, 0), LocalTime.of(17, 0), 60);
		assertThat(horario.franjas()).hasSize(8);
		assertThat(horario.franjas().getFirst())
				.isEqualTo(new HorarioTaller.Franja(1, LocalTime.of(9, 0), LocalTime.of(10, 0)));
		assertThat(horario.franja(8)).map(HorarioTaller.Franja::inicio).contains(LocalTime.of(16, 0));
		assertThat(horario.franja(9)).isEmpty();
		assertThat(horario.plazoParaCancelar()).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void unaFranjaQueNoCabeAntesDelCierreNoSeOfrece() {
		assertThat(horario(LocalTime.of(9, 0), LocalTime.of(10, 30), 60).franjas()).hasSize(1);
	}

	@Test
	void soloAbreLosDiasConfigurados() {
		HorarioTaller horario = horario(LocalTime.of(9, 0), LocalTime.of(17, 0), 60);
		assertThat(horario.abre(LocalDate.of(2026, 11, 23))).isTrue();
		assertThat(horario.abre(LocalDate.of(2026, 11, 22))).isFalse();
	}

	@Test
	void rechazaUnaConfiguracionInvalida() {
		assertThatThrownBy(() -> horario(LocalTime.of(17, 0), LocalTime.of(9, 0), 60))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> horario(LocalTime.of(9, 0), LocalTime.of(9, 30), 60))
				.isInstanceOf(IllegalStateException.class);
	}
}
