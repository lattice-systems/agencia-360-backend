package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TrasladoTest {

	private static final Instant AHORA = Instant.parse("2026-01-01T10:00:00Z");

	private static Traslado traslado(TipoTraslado tipo) {
		return Traslado.programar(1L, 2L, tipo, BigDecimal.TEN, LocalDate.of(2026, 1, 5), LocalTime.of(7, 0),
				LocalTime.of(9, 0));
	}

	@Test
	void unaRecoleccionVaDeProgramadoAEnTallerPasandoPorEnCaminoYRecogido() {
		Traslado traslado = traslado(TipoTraslado.RECOGER);
		traslado.cambiarEstado(EstadoTraslado.EN_CAMINO, 77L);
		traslado.cambiarEstado(EstadoTraslado.RECOGIDO, 77L);
		traslado.cambiarEstado(EstadoTraslado.EN_TALLER, 77L);
		assertThat(traslado.getEstado()).isEqualTo(EstadoTraslado.EN_TALLER);
		assertThat(traslado.getChoferId()).isEqualTo(77L);
	}

	@Test
	void unaEntregaVaDeProgramadoAEntregadoPasandoPorDeRegreso() {
		Traslado traslado = traslado(TipoTraslado.ENTREGAR);
		traslado.cambiarEstado(EstadoTraslado.DE_REGRESO, 78L);
		traslado.cambiarEstado(EstadoTraslado.ENTREGADO, 78L);
		assertThat(traslado.getEstado()).isEqualTo(EstadoTraslado.ENTREGADO);
	}

	@Test
	void noSaltaEstadosNiMezclaLosDeOtroTipo() {
		Traslado recoger = traslado(TipoTraslado.RECOGER);
		assertThatThrownBy(() -> recoger.cambiarEstado(EstadoTraslado.RECOGIDO, 1L))
				.isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> recoger.cambiarEstado(EstadoTraslado.DE_REGRESO, 1L))
				.isInstanceOf(ConflictoException.class);
		Traslado entregar = traslado(TipoTraslado.ENTREGAR);
		assertThatThrownBy(() -> entregar.cambiarEstado(EstadoTraslado.EN_CAMINO, 1L))
				.isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> entregar.cambiarEstado(EstadoTraslado.CANCELADO, 1L))
				.isInstanceOf(ConflictoException.class);
	}

	@Test
	void laUbicacionSoloSeAceptaEnCaminoODeRegresoYSeBorraAlSalir() {
		Traslado traslado = traslado(TipoTraslado.RECOGER);
		assertThatThrownBy(() -> traslado.reportarUbicacion(new BigDecimal("21.1"), new BigDecimal("-101.6"), AHORA))
				.isInstanceOf(ConflictoException.class);
		traslado.cambiarEstado(EstadoTraslado.EN_CAMINO, 5L);
		traslado.reportarUbicacion(new BigDecimal("21.1"), new BigDecimal("-101.6"), AHORA);
		assertThat(traslado.getChoferLat()).isEqualByComparingTo("21.1");
		assertThat(traslado.getUbicacionEn()).isEqualTo(AHORA);
		traslado.cambiarEstado(EstadoTraslado.RECOGIDO, 5L);
		assertThat(traslado.getChoferLat()).isNull();
		assertThat(traslado.getChoferLng()).isNull();
		assertThat(traslado.getUbicacionEn()).isNull();
	}

	@Test
	void rechazaUbicacionFueraDeRango() {
		Traslado traslado = traslado(TipoTraslado.RECOGER);
		traslado.cambiarEstado(EstadoTraslado.EN_CAMINO, 5L);
		assertThatThrownBy(() -> traslado.reportarUbicacion(new BigDecimal("91"), BigDecimal.ZERO, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> traslado.reportarUbicacion(BigDecimal.ZERO, new BigDecimal("181"), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cancelarSoloAplicaAlProgramadoYNoTocaLosQueYaSalieron() {
		Traslado programado = traslado(TipoTraslado.RECOGER);
		assertThat(programado.cancelarPorCita()).isTrue();
		assertThat(programado.getEstado()).isEqualTo(EstadoTraslado.CANCELADO);
		Traslado enCamino = traslado(TipoTraslado.RECOGER);
		enCamino.cambiarEstado(EstadoTraslado.EN_CAMINO, 5L);
		assertThat(enCamino.cancelarPorCita()).isFalse();
		assertThat(enCamino.getEstado()).isEqualTo(EstadoTraslado.EN_CAMINO);
	}

	@Test
	void reprogramarMueveLaFechaSoloSiSigueProgramado() {
		Traslado programado = traslado(TipoTraslado.RECOGER);
		programado.reprogramar(LocalDate.of(2026, 1, 9));
		assertThat(programado.getFecha()).isEqualTo(LocalDate.of(2026, 1, 9));
		assertThat(programado.getVentanaInicio()).isEqualTo(LocalTime.of(7, 0));
		Traslado enCamino = traslado(TipoTraslado.RECOGER);
		enCamino.cambiarEstado(EstadoTraslado.EN_CAMINO, 5L);
		enCamino.reprogramar(LocalDate.of(2026, 1, 9));
		assertThat(enCamino.getFecha()).isEqualTo(LocalDate.of(2026, 1, 5));
	}

	@Test
	void elChoferSeGuardaEnLaPrimeraVezYNoSeSobrescribe() {
		Traslado traslado = traslado(TipoTraslado.RECOGER);
		traslado.cambiarEstado(EstadoTraslado.EN_CAMINO, 5L);
		traslado.cambiarEstado(EstadoTraslado.RECOGIDO, 6L);
		assertThat(traslado.getChoferId()).isEqualTo(5L);
	}
}
