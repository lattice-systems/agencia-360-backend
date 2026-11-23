package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HorarioTallerTest {

	private final FranjaHorarioRepository repositorio = mock(FranjaHorarioRepository.class);

	private static FranjaHorario franja(DayOfWeek dia, int hora) {
		return FranjaHorario.nueva(dia, LocalTime.of(hora, 0), LocalTime.of(hora + 1, 0), 3);
	}

	@Test
	void agrupaLasFranjasActivasPorDiaEnSuOrden() {
		FranjaHorario lunesNueve = franja(DayOfWeek.MONDAY, 9);
		FranjaHorario lunesDiez = franja(DayOfWeek.MONDAY, 10);
		FranjaHorario sabadoNueve = franja(DayOfWeek.SATURDAY, 9);
		when(repositorio.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc())
				.thenReturn(List.of(lunesNueve, lunesDiez, sabadoNueve));

		Map<DayOfWeek, List<FranjaHorario>> porDia = new HorarioTaller(repositorio, 24, 60).franjasPorDia();

		assertThat(porDia).containsOnlyKeys(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
		assertThat(porDia.get(DayOfWeek.MONDAY)).containsExactly(lunesNueve, lunesDiez);
		assertThat(porDia.get(DayOfWeek.SATURDAY)).containsExactly(sabadoNueve);
		assertThat(porDia.get(DayOfWeek.SUNDAY)).isNull();
	}

	@Test
	void buscaSoloFranjasActivasPorSuId() {
		FranjaHorario lunesNueve = franja(DayOfWeek.MONDAY, 9);
		when(repositorio.findByIdAndActivaTrue(7L)).thenReturn(Optional.of(lunesNueve));
		when(repositorio.findByIdAndActivaTrue(8L)).thenReturn(Optional.empty());
		HorarioTaller horario = new HorarioTaller(repositorio, 24, 60);

		assertThat(horario.franja(7L)).contains(lunesNueve);
		assertThat(horario.franja(8L)).isEmpty();
	}

	@Test
	void sinIdNoConsultaLaTabla() {
		assertThat(new HorarioTaller(repositorio, 24, 60).franja(null)).isEmpty();
		verifyNoInteractions(repositorio);
	}

	@Test
	void conservaElPlazoParaCancelarYLaAnticipacion() {
		HorarioTaller horario = new HorarioTaller(repositorio, 12, 30);

		assertThat(horario.plazoParaCancelar()).isEqualTo(Duration.ofHours(12));
		assertThat(horario.diasDeAnticipacion()).isEqualTo(30);
	}

	@Test
	void rechazaUnaConfiguracionInvalida() {
		assertThatThrownBy(() -> new HorarioTaller(repositorio, -1, 60)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new HorarioTaller(repositorio, 24, 0)).isInstanceOf(IllegalStateException.class);
	}
}
