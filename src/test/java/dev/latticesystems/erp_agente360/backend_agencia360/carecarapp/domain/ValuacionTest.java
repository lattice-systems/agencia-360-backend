package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Estados, vigencia y fotos de la valuación formal (V2-M26.01 a M26.04). */
class ValuacionTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 25);
	private static final Instant AHORA = Instant.parse("2026-11-25T18:00:00Z");
	private static final int DIAS_SIN_OFERTA = 7;
	private static final int VIGENCIA_MAXIMA = 30;
	private static final BigDecimal MONTO = new BigDecimal("150000.00");

	@Test
	void naceSolicitadaSinCitaNiOfertaYAdmiteFotosYCita() {
		Valuacion nueva = solicitada();

		assertThat(nueva.getEstado()).isEqualTo(EstadoValuacion.SOLICITADA);
		assertThat(nueva.getCitaId()).isNull();
		assertThat(nueva.getOferta()).isNull();
		assertThat(nueva.getFotos()).isEmpty();
		assertThat(nueva.puedeAgendar(null, HOY, DIAS_SIN_OFERTA)).isTrue();
	}

	@Test
	void volverASubirUnaTomaLaReemplazaYDevuelveLaClaveAnterior() {
		Valuacion valuacion = solicitada();

		assertThat(valuacion.ponerFoto(TomaValuacion.FRENTE, "valuacion-fotos/a", AHORA)).isEmpty();
		assertThat(valuacion.ponerFoto(TomaValuacion.CAJUELA, "valuacion-fotos/c", AHORA)).isEmpty();
		assertThat(valuacion.ponerFoto(TomaValuacion.FRENTE, "valuacion-fotos/b", AHORA.plusSeconds(5)))
				.contains("valuacion-fotos/a");

		assertThat(valuacion.getFotos()).containsOnlyKeys(TomaValuacion.FRENTE, TomaValuacion.CAJUELA);
		assertThat(valuacion.getFotos().get(TomaValuacion.FRENTE))
				.isEqualTo(new FotoGuardada("valuacion-fotos/b", AHORA.plusSeconds(5)));
	}

	@Test
	void lasOchoTomasVanEnElOrdenDelContrato() {
		assertThat(TomaValuacion.values()).extracting(Enum::name).containsExactly("FRENTE", "TRASERA",
				"LATERAL_IZQUIERDO", "LATERAL_DERECHO", "INTERIOR_DELANTERO", "INTERIOR_TRASERO", "TABLERO_KM",
				"CAJUELA");
	}

	@Test
	void soloAdmiteFotosSolicitadaOConCita() {
		Valuacion conCita = conCita();
		conCita.ponerFoto(TomaValuacion.FRENTE, "valuacion-fotos/a", AHORA);

		for (Valuacion cerrada : List.of(ofertada(), aceptada(), rechazada(), vencida())) {
			assertThatThrownBy(() -> cerrada.ponerFoto(TomaValuacion.FRENTE, "valuacion-fotos/x", AHORA))
					.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo("VALUACION_CERRADA");
		}
	}

	@Test
	void agendarDejaLaCitaYYaNoSePuedeAgendarOtraAntesDelPlazo() {
		Valuacion valuacion = conCita();

		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.CITA_AGENDADA);
		assertThat(valuacion.getCitaId()).isEqualTo(50L);
		assertThat(valuacion.puedeAgendar(HOY.plusDays(2), HOY, DIAS_SIN_OFERTA)).isFalse();
		// El día 7 todavía no es "hace más de 7 días".
		assertThat(valuacion.puedeAgendar(HOY.minusDays(7), HOY, DIAS_SIN_OFERTA)).isFalse();
		assertThat(valuacion.puedeAgendar(HOY.minusDays(8), HOY, DIAS_SIN_OFERTA)).isTrue();
		assertThatThrownBy(() -> valuacion.exigirAgendable(HOY, HOY, DIAS_SIN_OFERTA))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo("VALUACION_NO_AGENDABLE");
	}

	@Test
	void unaValuacionConOfertaNoSeAgenda() {
		for (Valuacion cerrada : List.of(ofertada(), aceptada(), rechazada(), vencida())) {
			assertThat(cerrada.puedeAgendar(HOY.minusDays(30), HOY, DIAS_SIN_OFERTA)).isFalse();
		}
	}

	@Test
	void laCitaCanceladaLaRegresaASolicitada() {
		Valuacion valuacion = conCita();

		assertThat(valuacion.reabrirSinCita()).isTrue();

		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.SOLICITADA);
		assertThat(valuacion.getCitaId()).isNull();
		assertThat(ofertada().reabrirSinCita()).isFalse();
		assertThat(aceptada().reabrirSinCita()).isFalse();
		assertThat(solicitada().reabrirSinCita()).isFalse();
	}

	@Test
	void seOfertaConLaInspeccionHechaYQuedaElRastroDeQuienLaCapturo() {
		Valuacion valuacion = conCita();

		ofertar(valuacion, true);

		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.OFERTADA);
		assertThat(valuacion.getOferta()).isEqualByComparingTo(MONTO);
		assertThat(valuacion.getVigenciaHasta()).isEqualTo(HOY.plusDays(7));
		assertThat(valuacion.getOfertaNota()).isEqualTo("Sujeta a papeles en regla");
		assertThat(valuacion.getOfertadaPor()).isEqualTo(9L);
		assertThat(valuacion.getOfertadaEn()).isEqualTo(AHORA);
	}

	@Test
	void noSeOfertaSinInspeccionNiConLaOfertaVigenteNiYaRespondida() {
		for (Valuacion prohibida : List.of(solicitada(), ofertada(), aceptada(), rechazada())) {
			assertThatThrownBy(() -> ofertar(prohibida, true)).isInstanceOf(ConflictoException.class)
					.extracting("codigo").isEqualTo("TRANSICION_INVALIDA");
		}
		assertThatThrownBy(() -> ofertar(conCita(), false)).isInstanceOf(ConflictoException.class).extracting("codigo")
				.isEqualTo("TRANSICION_INVALIDA");
	}

	@Test
	void unaOfertaVencidaSePuedeRenovar() {
		Valuacion valuacion = vencida();

		valuacion.ofertar(new BigDecimal("140000"), HOY.plusDays(20), null, 9L, AHORA, false);

		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.OFERTADA);
		assertThat(valuacion.getOferta()).isEqualByComparingTo("140000");
		assertThat(valuacion.getOfertaNota()).isNull();
	}

	@Test
	void validaElMontoLaVigenciaYLaNota() {
		for (String monto : new String[]{"0", "-1", "100.005", "10000000000"}) {
			assertThatThrownBy(() -> Valuacion.validarOferta(new BigDecimal(monto), HOY, null, HOY, VIGENCIA_MAXIMA))
					.as(monto).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> Valuacion.validarOferta(null, HOY, null, HOY, VIGENCIA_MAXIMA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Valuacion.validarOferta(MONTO, HOY.minusDays(1), null, HOY, VIGENCIA_MAXIMA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Valuacion.validarOferta(MONTO, HOY.plusDays(31), null, HOY, VIGENCIA_MAXIMA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Valuacion.validarOferta(MONTO, HOY, "a".repeat(501), HOY, VIGENCIA_MAXIMA))
				.isInstanceOf(IllegalArgumentException.class);

		Valuacion.validarOferta(MONTO, HOY, "a".repeat(500), HOY, VIGENCIA_MAXIMA);
		Valuacion.validarOferta(new BigDecimal("100.500"), HOY.plusDays(30), null, HOY, VIGENCIA_MAXIMA);
	}

	@Test
	void laOfertaValeElUltimoDiaYVenceAlSiguiente() {
		Valuacion valuacion = ofertada();

		assertThat(valuacion.vencerSiPaso(HOY.plusDays(7))).isFalse();
		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.OFERTADA);
		assertThat(valuacion.vencerSiPaso(HOY.plusDays(8))).isTrue();
		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.VENCIDA);
		assertThat(aceptada().vencerSiPaso(HOY.plusYears(1))).isFalse();
	}

	@Test
	void seRespondeElUltimoDiaDeVigencia() {
		Valuacion valuacion = ofertada();

		valuacion.responder(true, new BigDecimal("150000"), HOY.plusDays(7), AHORA);

		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.ACEPTADA);
		assertThat(valuacion.getRespondidaEn()).isEqualTo(AHORA);
	}

	@Test
	void alDiaSiguienteLaOfertaYaVencio() {
		assertThatThrownBy(() -> ofertada().responder(true, MONTO, HOY.plusDays(8), AHORA))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo("OFERTA_VENCIDA");
		assertThatThrownBy(() -> vencida().responder(false, MONTO, HOY, AHORA)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo("OFERTA_VENCIDA");
	}

	@Test
	void repetirLaMismaRespuestaNoCambiaNada() {
		Valuacion valuacion = rechazada();

		valuacion.responder(false, MONTO, HOY.plusYears(1), AHORA.plusSeconds(900));

		assertThat(valuacion.getEstado()).isEqualTo(EstadoValuacion.RECHAZADA);
		assertThat(valuacion.getRespondidaEn()).isEqualTo(AHORA);
	}

	@Test
	void laRespuestaContrariaOtroMontoOSinOfertaNoSeRegistra() {
		assertNoDisponible(rechazada(), true, MONTO);
		assertNoDisponible(aceptada(), false, MONTO);
		assertNoDisponible(aceptada(), true, new BigDecimal("1"));
		assertNoDisponible(solicitada(), true, MONTO);
		assertNoDisponible(conCita(), true, MONTO);
		Valuacion vigente = ofertada();
		assertNoDisponible(vigente, true, new BigDecimal("149999.99"));
		assertThat(vigente.getEstado()).isEqualTo(EstadoValuacion.OFERTADA);
		assertThat(vigente.getRespondidaEn()).isNull();
	}

	private static void assertNoDisponible(Valuacion valuacion, boolean aceptada, BigDecimal monto) {
		assertThatThrownBy(() -> valuacion.responder(aceptada, monto, HOY, AHORA))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo("OFERTA_NO_DISPONIBLE");
	}

	private static Valuacion solicitada() {
		return Valuacion.solicitar(1L, 2L, 3L);
	}

	private static Valuacion conCita() {
		Valuacion valuacion = solicitada();
		valuacion.inspeccionAgendada(50L);
		return valuacion;
	}

	private static void ofertar(Valuacion valuacion, boolean inspeccionHecha) {
		valuacion.ofertar(MONTO, HOY.plusDays(7), "  Sujeta a papeles en regla ", 9L, AHORA, inspeccionHecha);
	}

	private static Valuacion ofertada() {
		Valuacion valuacion = conCita();
		ofertar(valuacion, true);
		return valuacion;
	}

	private static Valuacion aceptada() {
		Valuacion valuacion = ofertada();
		valuacion.responder(true, MONTO, HOY, AHORA);
		return valuacion;
	}

	private static Valuacion rechazada() {
		Valuacion valuacion = ofertada();
		valuacion.responder(false, MONTO, HOY, AHORA);
		return valuacion;
	}

	private static Valuacion vencida() {
		Valuacion valuacion = ofertada();
		valuacion.vencerSiPaso(HOY.plusDays(8));
		return valuacion;
	}
}
