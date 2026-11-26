package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.AvisoGarantia;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Garantia;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PoliticaGarantiaTest {

	private static final LocalDate INICIO = LocalDate.of(2024, 1, 1);
	private static final LocalDate VENCE = LocalDate.of(2027, 1, 1);

	private static Garantia motor(Integer kmLimite) {
		return Garantia.registrar(7L, "Motor", null, INICIO, VENCE, kmLimite);
	}

	@Test
	void vigenteConElConsumoDelLimiteQueVaMasAdelante() {
		CoberturaApp cobertura = PoliticaGarantia.evaluar(motor(60_000), 45_000, LocalDate.of(2025, 7, 2));

		assertThat(cobertura.estado()).isEqualTo(EstadoCobertura.VIGENTE);
		assertThat(cobertura.kmRestantes()).isEqualTo(15_000);
		assertThat(cobertura.diasRestantes()).isEqualTo(548);
		assertThat(cobertura.consumo()).isEqualTo(75);
		assertThat(cobertura.limite()).isEqualTo("km");
	}

	@Test
	void porVencerA60DiasOA1000Km() {
		assertThat(PoliticaGarantia.evaluar(motor(null), null, LocalDate.of(2026, 11, 2)).estado())
				.isEqualTo(EstadoCobertura.POR_VENCER);
		assertThat(PoliticaGarantia.evaluar(motor(null), null, LocalDate.of(2026, 11, 1)).estado())
				.isEqualTo(EstadoCobertura.VIGENTE);
		CoberturaApp porKm = PoliticaGarantia.evaluar(motor(60_000), 59_000, LocalDate.of(2025, 1, 1));
		assertThat(porKm.estado()).isEqualTo(EstadoCobertura.POR_VENCER);
		assertThat(porKm.kmRestantes()).isEqualTo(1_000);
	}

	@Test
	void vencidaPorFechaOPorKmDiceCualLaVencio() {
		CoberturaApp porFecha = PoliticaGarantia.evaluar(motor(60_000), 10_000, LocalDate.of(2027, 1, 2));
		assertThat(porFecha.estado()).isEqualTo(EstadoCobertura.VENCIDA);
		assertThat(porFecha.limite()).isEqualTo("fecha");
		assertThat(porFecha.diasRestantes()).isZero();
		assertThat(porFecha.consumo()).isEqualTo(100);

		CoberturaApp porKm = PoliticaGarantia.evaluar(motor(60_000), 61_000, LocalDate.of(2025, 1, 1));
		assertThat(porKm.estado()).isEqualTo(EstadoCobertura.VENCIDA);
		assertThat(porKm.limite()).isEqualTo("km");
		assertThat(porKm.kmRestantes()).isZero();
	}

	@Test
	void sinKmConocidoSoloCuentaLaFecha() {
		CoberturaApp cobertura = PoliticaGarantia.evaluar(motor(60_000), null, LocalDate.of(2025, 7, 2));

		assertThat(cobertura.kmRestantes()).isNull();
		assertThat(cobertura.limite()).isEqualTo("fecha");
		assertThat(cobertura.consumo()).isEqualTo(50);
	}

	@Test
	void avisaPrimeroLoMasUrgenteYCadaAvisoUnaVez() {
		Garantia garantia = motor(60_000);
		LocalDate a10Dias = LocalDate.of(2026, 12, 22);

		assertThat(PoliticaGarantia.avisoPendiente(garantia, 59_500, a10Dias)).contains(AvisoGarantia.DIAS_15);
		garantia.marcarAvisado(AvisoGarantia.DIAS_15, Instant.EPOCH);
		assertThat(PoliticaGarantia.avisoPendiente(garantia, 59_500, a10Dias)).contains(AvisoGarantia.KM_1000);
		garantia.marcarAvisado(AvisoGarantia.KM_1000, Instant.EPOCH);
		assertThat(PoliticaGarantia.avisoPendiente(garantia, 59_500, a10Dias)).isEmpty();
	}

	@Test
	void a60DiasAvisaUnaVezYVencidaYaNoAvisa() {
		Garantia garantia = motor(null);

		assertThat(PoliticaGarantia.avisoPendiente(garantia, null, LocalDate.of(2026, 9, 1))).isEmpty();
		assertThat(PoliticaGarantia.avisoPendiente(garantia, null, LocalDate.of(2026, 11, 2)))
				.contains(AvisoGarantia.DIAS_60);
		assertThat(PoliticaGarantia.avisoPendiente(garantia, null, LocalDate.of(2027, 1, 2))).isEmpty();
	}
}
