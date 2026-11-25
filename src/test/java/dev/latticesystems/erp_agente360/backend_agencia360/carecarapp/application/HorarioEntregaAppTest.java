package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HorarioEntregaAppTest {

	private static final Map<DayOfWeek, List<FranjaHorario>> ENTRE_SEMANA = Map.of(DayOfWeek.TUESDAY,
			List.of(franja(DayOfWeek.TUESDAY, 9, 10), franja(DayOfWeek.TUESDAY, 16, 17)), DayOfWeek.WEDNESDAY,
			List.of(franja(DayOfWeek.WEDNESDAY, 10, 11)));

	private static FranjaHorario franja(DayOfWeek dia, int desde, int hasta) {
		return FranjaHorario.nueva(dia, LocalTime.of(desde, 0), LocalTime.of(hasta, 0), 3);
	}

	@Test
	void hoyVaDeLaPrimeraALaUltimaHoraDelTaller() {
		LocalDateTime martesEnLaManana = LocalDateTime.of(2026, 11, 24, 8, 0);

		assertThat(HorarioEntregaApp.siguiente(ENTRE_SEMANA, martesEnLaManana))
				.contains(new HorarioEntregaApp(LocalDate.of(2026, 11, 24), LocalTime.of(9, 0), LocalTime.of(17, 0)));
	}

	@Test
	void siHoyYaCerroPasaAlSiguienteDiaConHorario() {
		LocalDateTime martesEnLaNoche = LocalDateTime.of(2026, 11, 24, 17, 0);

		assertThat(HorarioEntregaApp.siguiente(ENTRE_SEMANA, martesEnLaNoche))
				.contains(new HorarioEntregaApp(LocalDate.of(2026, 11, 25), LocalTime.of(10, 0), LocalTime.of(11, 0)));
	}

	@Test
	void saltaLosDiasSinHorarioYSinNingunoNoHayEntrega() {
		LocalDateTime jueves = LocalDateTime.of(2026, 11, 26, 9, 0);

		assertThat(HorarioEntregaApp.siguiente(ENTRE_SEMANA, jueves))
				.contains(new HorarioEntregaApp(LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), LocalTime.of(17, 0)));
		assertThat(HorarioEntregaApp.siguiente(Map.of(), jueves)).isEmpty();
	}
}
