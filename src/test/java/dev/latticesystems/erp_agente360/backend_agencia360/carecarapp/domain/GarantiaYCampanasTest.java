package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GarantiaYCampanasTest {

	private static final LocalDate INICIO = LocalDate.of(2024, 1, 15);
	private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");

	private static CampanaSeguridad.Datos datos(String vinDesde, String vinHasta) {
		return new CampanaSeguridad.Datos(" rc-24-017 ", "Bolsa de aire del conductor", "El inflador puede fallar.",
				"Agenda una revisión; el cambio no tiene costo.", GravedadCampana.CRITICA, "March", 2023, 2026,
				vinDesde, vinHasta);
	}

	@Test
	void unaGarantiaLimpiaSusTextosYValidaFechasYKm() {
		Garantia motor = Garantia.registrar(7L, "  Motor y transmisión ", " ", INICIO, INICIO.plusYears(3), 60_000);

		assertThat(motor.getComponente()).isEqualTo("Motor y transmisión");
		assertThat(motor.getDescripcion()).isNull();
		assertThat(motor.getKmLimite()).isEqualTo(60_000);
		assertThatThrownBy(() -> Garantia.registrar(7L, "Motor", null, INICIO, INICIO.minusDays(1), null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Garantia.registrar(7L, " ", null, INICIO, INICIO, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Garantia.registrar(7L, "Motor", null, INICIO, INICIO, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Garantia.registrar(null, "Motor", null, INICIO, INICIO, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elAvisoDe15DiasCierraTambienElDe60() {
		Garantia motor = Garantia.registrar(7L, "Motor", null, INICIO, INICIO.plusYears(3), 60_000);

		motor.marcarAvisado(AvisoGarantia.DIAS_15, AHORA);

		assertThat(motor.avisado(AvisoGarantia.DIAS_15)).isTrue();
		assertThat(motor.avisado(AvisoGarantia.DIAS_60)).isTrue();
		assertThat(motor.avisado(AvisoGarantia.KM_1000)).isFalse();
		motor.marcarAvisado(AvisoGarantia.KM_1000, AHORA);
		assertThat(motor.avisado(AvisoGarantia.KM_1000)).isTrue();
	}

	@Test
	void unaCampanaAplicaPorModeloAnioYRangoDeVin() {
		CampanaSeguridad sinRango = CampanaSeguridad.publicar(datos(null, null), AHORA);
		CampanaSeguridad conRango = CampanaSeguridad.publicar(datos("3N1CK3CD5RL000100", "3N1CK3CD5RL000199"), AHORA);

		assertThat(sinRango.getCodigo()).isEqualTo("RC-24-017");
		assertThat(sinRango.aplicaA("march", 2024, "CUALQUIERA")).isTrue();
		assertThat(sinRango.aplicaA("Versa", 2024, null)).isFalse();
		assertThat(sinRango.aplicaA("March", 2022, null)).isFalse();
		assertThat(sinRango.aplicaA("March", 2027, null)).isFalse();
		assertThat(conRango.aplicaA("March", 2025, "3n1ck3cd5rl000150")).isTrue();
		assertThat(conRango.aplicaA("March", 2025, "3N1CK3CD5RL000100")).isTrue();
		assertThat(conRango.aplicaA("March", 2025, "3N1CK3CD5RL000199")).isTrue();
		assertThat(conRango.aplicaA("March", 2025, "3N1CK3CD5RL000200")).isFalse();
		assertThat(conRango.aplicaA("March", 2025, null)).isFalse();
	}

	@Test
	void unaCampanaRechazaDatosIncompletosOAlReves() {
		assertThatThrownBy(() -> CampanaSeguridad.publicar(datos("3N1CK3CD5RL000100", null), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CampanaSeguridad.publicar(datos("3N1CK3CD5RL000199", "3N1CK3CD5RL000100"), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CampanaSeguridad.publicar(datos("CORTO", "CORTO2"), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CampanaSeguridad.publicar(
				new CampanaSeguridad.Datos("RC-1", "T", "D", "Q", null, "March", 2024, 2025, null, null), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CampanaSeguridad.publicar(new CampanaSeguridad.Datos("RC-1", "T", "D", "Q",
				GravedadCampana.BAJA, "March", 2026, 2024, null, null), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void laCampanaDelVehiculoSeAgendaSeReabreYSeAtiende() {
		CampanaVehiculo delAuto = new CampanaVehiculo(1L, 7L);
		assertThat(delAuto.getEstado()).isEqualTo(EstadoCampana.ABIERTA);

		delAuto.agendar(40L);
		assertThat(delAuto.getEstado()).isEqualTo(EstadoCampana.AGENDADA);
		assertThat(delAuto.getCitaId()).isEqualTo(40L);
		assertThatThrownBy(() -> delAuto.agendar(41L)).isInstanceOf(IllegalStateException.class);

		delAuto.reabrir();
		assertThat(delAuto.getEstado()).isEqualTo(EstadoCampana.ABIERTA);
		assertThat(delAuto.getCitaId()).isNull();

		delAuto.atender();
		delAuto.reabrir();
		assertThat(delAuto.getEstado()).isEqualTo(EstadoCampana.ATENDIDA);
		assertThatThrownBy(() -> delAuto.agendar(42L)).isInstanceOf(IllegalStateException.class);
	}
}
