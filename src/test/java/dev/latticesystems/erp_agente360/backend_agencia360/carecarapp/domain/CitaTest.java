package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CitaTest {

	private static final LocalDate DIA = LocalDate.of(2026, 11, 25);
	private static final Duration PLAZO = Duration.ofHours(24);

	private static Cita cita() {
		return Cita.confirmar(1L, 2L, DIA, 1L, LocalTime.of(9, 0), LocalTime.of(10, 0));
	}

	@Test
	void naceConfirmada() {
		assertThat(cita().getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
	}

	@Test
	void seCancelaHastaElPlazoAntesDeEmpezar() {
		Cita cita = cita();
		cita.cancelar(DIA.minusDays(1).atTime(9, 0), PLAZO, Instant.EPOCH);
		assertThat(cita.getEstado()).isEqualTo(EstadoCita.CANCELADA);
		assertThat(cita.getCanceladaEn()).isEqualTo(Instant.EPOCH);
	}

	@Test
	void fueraDelPlazoNoSeCancela() {
		Cita cita = cita();
		LocalDateTime tarde = DIA.minusDays(1).atTime(9, 1);
		assertThatThrownBy(() -> cita.cancelar(tarde, PLAZO, Instant.EPOCH)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo("FUERA_DE_PLAZO");
		assertThat(cita.getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
	}

	@Test
	void unaCitaCanceladaNoSeVuelveACancelar() {
		Cita cita = cita();
		cita.cancelar(DIA.minusDays(5).atStartOfDay(), PLAZO, Instant.EPOCH);
		assertThatThrownBy(() -> cita.cancelar(DIA.minusDays(5).atStartOfDay(), PLAZO, Instant.EPOCH))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo("CITA_NO_CANCELABLE");
	}

	@Test
	void laFranjaDebeTerminarDespuesDeEmpezar() {
		assertThatThrownBy(() -> Cita.confirmar(1L, 2L, DIA, 1L, LocalTime.of(10, 0), LocalTime.of(10, 0)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
