package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp.Cobrable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Estados de un pago y las tarjetas de prueba (V2-M08.02 y V2-M08.04). */
class PagoAppTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
	private static final Cobrable ORDEN = new Cobrable(41L, 9L, 3L, new BigDecimal("1650.00"));
	private static final YearMonth VENCE = YearMonth.of(2028, 12);

	@Test
	void lasTarjetasDePruebaDicenMarcaUltimosCuatroYQueHaceElBanco() {
		TarjetaDePrueba visa = TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow();
		assertThat(visa.marca()).isEqualTo("VISA");
		assertThat(visa.ultimos4()).isEqualTo("4242");
		assertThat(visa.pideAutenticacion()).isFalse();
		assertThat(TarjetaDePrueba.deToken("tok_sim_visa_debito").orElseThrow().tipo()).isEqualTo(TipoTarjeta.DEBITO);
		assertThat(TarjetaDePrueba.deToken("tok_sim_3ds").orElseThrow().pideAutenticacion()).isTrue();
		assertThat(TarjetaDePrueba.deToken("tok_sim_3ds_falla").orElseThrow().pideAutenticacion()).isTrue();
		assertThat(TarjetaDePrueba.deToken("4242424242424242")).isEmpty();
		assertThat(TarjetaDePrueba.todas()).extracting(TarjetaDePrueba::token).doesNotHaveDuplicates();
	}

	@Test
	void naceGuardandoSoloLoQueNoEsElNumeroYQuedaPagado() {
		PagoApp pago = conTarjeta("tok_sim_visa", true);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(pago.getMedio()).isEqualTo(MedioPago.TARJETA);
		assertThat(pago.getMoneda()).isEqualTo("MXN");
		assertThat(pago.getUltimos4()).isEqualTo("4242");
		assertThat(pago.getVence()).isEqualTo(VENCE);
		assertThat(pago.isGuardarTarjeta()).isTrue();
		assertThat(pago.getReferencia()).isEqualTo("sim_pi_1");

		pago.aprobar(AHORA);
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(pago.getPagadoEn()).isEqualTo(AHORA);
		assertThatThrownBy(() -> pago.fallar(MotivoFalloPago.FONDOS_INSUFICIENTES, AHORA))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void elTresDeSecureQuedaPendienteYTerminaAprobadoORechazado() {
		PagoApp aprobado = conTarjeta("tok_sim_3ds", false);
		aprobado.pedirAutenticacion(AHORA);
		assertThat(aprobado.requiereAutenticacion()).isTrue();
		aprobado.aprobar(AHORA);
		assertThat(aprobado.getAutenticacion()).isEqualTo(AutenticacionPago.APROBADA);
		assertThat(aprobado.requiereAutenticacion()).isFalse();

		PagoApp rechazado = conTarjeta("tok_sim_3ds", false);
		rechazado.pedirAutenticacion(AHORA);
		rechazado.fallar(MotivoFalloPago.AUTENTICACION_FALLIDA, AHORA);
		assertThat(rechazado.getEstado()).isEqualTo(EstadoPago.FALLIDO);
		assertThat(rechazado.getAutenticacion()).isEqualTo(AutenticacionPago.RECHAZADA);
		assertThat(rechazado.getMotivoFallo()).isEqualTo(MotivoFalloPago.AUTENTICACION_FALLIDA);
	}

	@Test
	void unPendienteVencePasadaSuVigencia() {
		PagoApp pago = conTarjeta("tok_sim_3ds", false);
		Duration vigencia = Duration.ofMinutes(15);

		assertThat(pago.vencido(AHORA.plus(Duration.ofMinutes(14)), vigencia)).isFalse();
		assertThat(pago.vencido(AHORA.plus(vigencia), vigencia)).isTrue();
		pago.fallar(MotivoFalloPago.AUTENTICACION_VENCIDA, AHORA);
		assertThat(pago.vencido(AHORA.plus(Duration.ofHours(1)), vigencia)).isFalse();
	}

	@Test
	void conUnaTarjetaGuardadaNoSeVuelveAGuardar() {
		PagoApp pago = PagoApp.conTarjeta(ORDEN, "SIMULADO", "sim_pi_2", "llave-123",
				TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow(), VENCE, 5L, true, null, AHORA);

		assertThat(pago.isGuardarTarjeta()).isFalse();
		assertThat(pago.getMetodoPagoId()).isEqualTo(5L);
	}

	@Test
	void enCajaNacePagadoConSuFolio() {
		PagoApp caja = PagoApp.enCaja(ORDEN, "caja_sim_pi_3", "F-100", 70L, AHORA);

		assertThat(caja.getEstado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(caja.getMedio()).isEqualTo(MedioPago.CAJA);
		assertThat(caja.getReferencia()).isEqualTo("F-100");
		assertThat(caja.getVence()).isNull();
		assertThat(PagoApp.enCaja(ORDEN, "caja_sim_pi_4", null, 70L, AHORA).getReferencia())
				.isEqualTo("caja_sim_pi_4");
	}

	@Test
	void unaTarjetaGuardadaVenceAlTerminarSuMes() {
		MetodoPago tarjeta = new MetodoPago(3L, "SIMULADO", "sim_cus_3",
				TarjetaDePrueba.deToken("tok_sim_mastercard").orElseThrow(), YearMonth.of(2026, 11), AHORA);

		assertThat(tarjeta.vencida(YearMonth.of(2026, 11))).isFalse();
		assertThat(tarjeta.vencida(YearMonth.of(2026, 12))).isTrue();
		tarjeta.renovar(YearMonth.of(2030, 1));
		assertThat(tarjeta.getMesVence()).isEqualTo(1);
		assertThat(tarjeta.getAnioVence()).isEqualTo(2030);
	}

	private static PagoApp conTarjeta(String token, boolean guardar) {
		return PagoApp.conTarjeta(ORDEN, "SIMULADO", "sim_pi_1", "llave-123",
				TarjetaDePrueba.deToken(token).orElseThrow(), VENCE, null, guardar, null, AHORA);
	}
}
