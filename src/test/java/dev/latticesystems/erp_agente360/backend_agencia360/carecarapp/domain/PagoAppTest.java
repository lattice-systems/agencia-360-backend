package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp.Cobrable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Estados de un pago, las tarjetas de prueba, las billeteras y la factura
 * (V2-M08.02, V2-M08.03, V2-M08.04 y V2-M08.06).
 */
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
		assertThat(PagoApp.enCaja(ORDEN, "caja_sim_pi_4", null, 70L, AHORA).getReferencia()).isEqualTo("caja_sim_pi_4");
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

	@Test
	void unPagoEsDeUnaOrdenODeUnPedidoYElDePedidoNoLlevaVehiculo() {
		Cobrable pedido = Cobrable.dePedido(15L, 3L, new BigDecimal("201.00"));
		PagoApp pago = PagoApp.conTarjeta(pedido, "SIMULADO", "sim_pi_2", "llave-1234",
				TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow(), VENCE, null, false, null, AHORA);

		assertThat(pago.getPedidoId()).isEqualTo(15L);
		assertThat(pago.getOrdenId()).isNull();
		assertThat(pago.getVehiculoId()).isNull();
		assertThat(pago.getMonto()).isEqualByComparingTo("201.00");
		assertThat(ORDEN.pedidoId()).isNull();
		assertThatThrownBy(() -> new Cobrable(41L, 9L, 3L, BigDecimal.TEN, 15L))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Cobrable(null, null, 3L, BigDecimal.TEN, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Cobrable(41L, null, 3L, BigDecimal.TEN, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void unaBilleteraSimuladaEsCreditoSinMarcaNiVencimientoYNoSeGuarda() {
		PagoApp pago = PagoApp.conBilletera(ORDEN, "SIMULADO", "sim_pi_3", "llave-billetera", MedioPago.APPLE_PAY,
				TarjetaDePrueba.deBilletera(MedioPago.APPLE_PAY), 10L, AHORA);

		assertThat(pago.getMedio()).isEqualTo(MedioPago.APPLE_PAY);
		assertThat(pago.getTipoTarjeta()).isEqualTo(TipoTarjeta.CREDITO);
		assertThat(pago.getMarca()).isNull();
		assertThat(pago.getUltimos4()).isNull();
		assertThat(pago.getVence()).isNull();
		assertThat(pago.isGuardarTarjeta()).isFalse();
		assertThat(pago.getTokenProcesador()).isEqualTo("tok_sim_apple_pay");
		assertThat(pago.getPerfilFiscalId()).isEqualTo(10L);
		assertThat(TarjetaDePrueba.deBilletera(MedioPago.GOOGLE_PAY).token()).isEqualTo("tok_sim_google_pay");
		assertThat(TarjetaDePrueba.deToken("tok_sim_apple_pay")).isEmpty();
		assertThatThrownBy(() -> TarjetaDePrueba.deBilletera(MedioPago.TARJETA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PagoApp.conBilletera(ORDEN, "SIMULADO", "sim_pi_4", "llave-billetera",
				MedioPago.TARJETA, TarjetaDePrueba.deBilletera(MedioPago.APPLE_PAY), null, AHORA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void soloSeFacturaUnPagoPagadoQueNoEsDeCaja() {
		PagoApp pago = conTarjeta("tok_sim_visa", false);
		assertThat(pago.facturable()).isFalse();

		pago.aprobar(AHORA);
		assertThat(pago.facturable()).isTrue();
		pago.elegirPerfilFiscal(10L, AHORA);
		assertThat(pago.getPerfilFiscalId()).isEqualTo(10L);

		Comprobante comprobante = new Comprobante("A", 124, LocalDateTime.of(2026, 11, 26, 12, 0),
				new Comprobante.Emisor("EKU9003173C9", "ESCUELA KEMPER URGATE", "601", "42501"),
				new Comprobante.Receptor("GODE561231GR8", "DOLORES GÓMEZ", "37000", "612", "G03"),
				List.of(Comprobante.Concepto.manoDeObra("Afinación", 1, new BigDecimal("1650.00"))),
				Comprobante.FORMA_CREDITO);
		UUID uuid = UUID.randomUUID();
		FacturaApp factura = FacturaApp.timbrada(pago, 10L, comprobante, uuid, AHORA, "facturas/xml", "facturas/pdf");
		assertThat(pago.getFactura()).isSameAs(factura);
		assertThat(pago.facturable()).isFalse();
		assertThat(factura.getTipo()).isEqualTo(ObjetoFacturado.SERVICIO);
		assertThat(factura.getFolio()).isEqualTo("A-000124");
		assertThat(factura.getTotal()).isEqualByComparingTo("1650.00");
		assertThat(factura.getSubtotal().add(factura.getIva())).isEqualByComparingTo("1650.00");
		assertThat(factura.getUuidFiscal()).isEqualTo(uuid);
		assertThat(factura.getReceptorRfc()).isEqualTo("GODE561231GR8");

		PagoApp enCaja = PagoApp.enCaja(ORDEN, "caja_1", "CAJA-1", 7L, AHORA);
		assertThat(enCaja.facturable()).isFalse();
	}
}
