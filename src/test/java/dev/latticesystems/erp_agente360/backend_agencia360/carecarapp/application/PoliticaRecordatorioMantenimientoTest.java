package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PoliticaRecordatorioMantenimientoTest {

	private final PoliticaRecordatorioMantenimiento politica = new PoliticaRecordatorioMantenimiento(1000, 14);

	private static ProximoServicioApp proximo(int faltanKm, int faltanDias, String motivo) {
		return new ProximoServicioApp(5L, "Servicio 20 000 km", 20_000, LocalDate.of(2026, 12, 20), faltanKm,
				faltanDias, motivo);
	}

	@Test
	void avisaCuandoFaltanMilKmOMenos() {
		assertThat(politica.motivo(proximo(1000, 90, "km"))).contains("km");
		assertThat(politica.motivo(proximo(400, 90, "km"))).contains("km");
	}

	@Test
	void avisaCuandoFaltanDosSemanasOMenos() {
		assertThat(politica.motivo(proximo(5000, 14, "tiempo"))).contains("tiempo");
		assertThat(politica.motivo(proximo(5000, 3, "tiempo"))).contains("tiempo");
	}

	@Test
	void sinAvisoSiTodaviaFaltaMucho() {
		assertThat(politica.motivo(proximo(1001, 15, "km"))).isEqualTo(Optional.empty());
	}

	@Test
	void sinProximoServicioNoHayAviso() {
		assertThat(politica.motivo(null)).isEqualTo(Optional.empty());
	}

	@Test
	void sielDosCriteriosSeCumplenGanaElKm() {
		assertThat(politica.motivo(proximo(200, 3, "km"))).contains("km");
	}

	@Test
	void unServicioYaVencidoTambienSeAvisa() {
		assertThat(politica.motivo(proximo(-50, -2, "km"))).contains("km");
	}
}
