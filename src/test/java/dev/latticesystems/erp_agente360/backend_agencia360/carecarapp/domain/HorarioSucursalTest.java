package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HorarioSucursalTest {

	// Lunes 2 de noviembre de 2026.
	private static final LocalDateTime LUNES_10 = LocalDateTime.of(2026, 11, 2, 10, 0);

	private final HorarioSucursal semana = new HorarioSucursal("SERVICIO", List.of(1, 2, 3, 4, 5), "08:00", "18:00");

	@Test
	void estaAbiertoDentroDelHorarioEnUnDiaDeLaLista() {
		assertThat(semana.abiertoEn(LUNES_10)).isTrue();
	}

	@Test
	void abreExactoALaHoraDeApertura() {
		assertThat(semana.abiertoEn(LUNES_10.withHour(8))).isTrue();
	}

	@Test
	void cierraExactoALaHoraDeCierre() {
		assertThat(semana.abiertoEn(LUNES_10.withHour(18))).isFalse();
		assertThat(semana.abiertoEn(LUNES_10.withHour(17).withMinute(59))).isTrue();
	}

	@Test
	void estaCerradoAntesDeAbrir() {
		assertThat(semana.abiertoEn(LUNES_10.withHour(7).withMinute(59))).isFalse();
	}

	@Test
	void estaCerradoUnDiaQueNoEstaEnLaLista() {
		assertThat(semana.abiertoEn(LUNES_10.plusDays(6))).isFalse();
	}

	@Test
	void rechazaUnDepartamentoDesconocido() {
		assertThatThrownBy(() -> new HorarioSucursal("COCINA", List.of(1), "08:00", "18:00"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaUnDiaFueraDeUnoASiete() {
		assertThatThrownBy(() -> new HorarioSucursal("VENTAS", List.of(0), "08:00", "18:00"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new HorarioSucursal("VENTAS", List.of(8), "08:00", "18:00"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaUnHorarioQueCierraAntesDeAbrir() {
		assertThatThrownBy(() -> new HorarioSucursal("VENTAS", List.of(1), "18:00", "08:00"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaUnaHoraMalEscrita() {
		assertThatThrownBy(() -> new HorarioSucursal("VENTAS", List.of(1), "8am", "18:00"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
