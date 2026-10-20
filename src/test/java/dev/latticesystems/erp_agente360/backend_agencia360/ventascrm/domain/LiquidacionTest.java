package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LiquidacionTest {

	private static final Long VENDEDOR = 7L;
	private static final Long CAJERA = 50L;
	private static final Long CONTADOR = 30L;
	private static final Instant AHORA = Instant.parse("2026-10-16T18:00:00Z");
	private static final LocalDate HOY = LocalDate.of(2026, 10, 16);

	private static Prospecto enPago() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);
		prospecto.moverPorVendedor(Etapa.PAGO, null, VENDEDOR, AHORA);
		return prospecto;
	}

	private static Liquidacion conUnPago() {
		Liquidacion liquidacion = Liquidacion.abrir(enPago());
		liquidacion.registrarPago(new BigDecimal("150000.00"), HOY, "TRF-001", CAJERA, HOY);
		return liquidacion;
	}

	@Test
	void seAbreParaUnProspectoEnPagoSinPagos() {
		Liquidacion liquidacion = Liquidacion.abrir(enPago());

		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.ABIERTA);
		assertThat(liquidacion.getPagos()).isEmpty();
		assertThat(liquidacion.total()).isEqualByComparingTo("0");
	}

	@Test
	void soloSeAbreSiElProspectoEstaEnPago() {
		Prospecto prospecto = Prospecto.registrar("Ana López", "4771234567", null, VENDEDOR, AHORA);

		assertThatThrownBy(() -> Liquidacion.abrir(prospecto)).isInstanceOf(ConflictoException.class)
				.hasMessage("Solo se registran pagos de un prospecto en Pago");
	}

	@Test
	void cajaRegistraPagosYSeSumanAlTotal() {
		Liquidacion liquidacion = conUnPago();

		liquidacion.registrarPago(new BigDecimal("339900.50"), HOY.minusDays(1), null, CAJERA, HOY);

		assertThat(liquidacion.getPagos()).hasSize(2);
		assertThat(liquidacion.total()).isEqualByComparingTo("489900.50");
		assertThat(liquidacion.getPagos().getFirst()).satisfies(pago -> {
			assertThat(pago.getMonto()).isEqualByComparingTo("150000.00");
			assertThat(pago.getFechaPago()).isEqualTo(HOY);
			assertThat(pago.getReferencia()).isEqualTo("TRF-001");
			assertThat(pago.getRegistradoPorId()).isEqualTo(CAJERA);
		});
	}

	@Test
	void rechazaMontosEnCeroNegativosOConMasDeDosDecimales() {
		Liquidacion liquidacion = Liquidacion.abrir(enPago());

		for (String monto : new String[]{"0", "-10", "10.555"}) {
			assertThatThrownBy(() -> liquidacion.registrarPago(new BigDecimal(monto), HOY, null, CAJERA, HOY))
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThat(liquidacion.getPagos()).isEmpty();
	}

	@Test
	void rechazaPagosConFechaFutura() {
		Liquidacion liquidacion = Liquidacion.abrir(enPago());

		assertThatThrownBy(() -> liquidacion.registrarPago(BigDecimal.TEN, HOY.plusDays(1), null, CAJERA, HOY))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("La fecha del pago no puede ser futura");
	}

	@Test
	void cajaAnulaUnPagoMalCapturadoSinBorrarlo() {
		Liquidacion liquidacion = conUnPago();
		liquidacion.registrarPago(new BigDecimal("1000"), HOY, null, CAJERA, HOY);
		Pago pago = liquidacion.getPagos().getFirst();
		ReflectionTestUtils.setField(pago, "id", 9L);

		liquidacion.anularPago(9L, CAJERA, AHORA);

		// El dinero no se borra: el pago se queda, marcado como anulado.
		assertThat(liquidacion.getPagos()).hasSize(2);
		assertThat(pago.isAnulado()).isTrue();
		assertThat(pago.getAnuladoPorId()).isEqualTo(CAJERA);
		assertThat(pago.getAnuladoEn()).isEqualTo(AHORA);
		assertThat(liquidacion.total()).isEqualByComparingTo("1000");
	}

	@Test
	void unPagoAnuladoNoSeVuelveAAnular() {
		Liquidacion liquidacion = conUnPago();
		ReflectionTestUtils.setField(liquidacion.getPagos().getFirst(), "id", 9L);
		liquidacion.anularPago(9L, CAJERA, AHORA);

		assertThatThrownBy(() -> liquidacion.anularPago(9L, CAJERA, AHORA)).isInstanceOf(ConflictoException.class)
				.hasMessage("Ese pago ya está anulado");
	}

	@Test
	void conTodosLosPagosAnuladosNoSeEnvia() {
		Liquidacion liquidacion = conUnPago();
		ReflectionTestUtils.setField(liquidacion.getPagos().getFirst(), "id", 9L);
		liquidacion.anularPago(9L, CAJERA, AHORA);

		assertThatThrownBy(() -> liquidacion.enviar(CAJERA, AHORA)).isInstanceOf(ConflictoException.class)
				.hasMessage("Registra al menos un pago antes de enviarlos a Contabilidad");
	}

	@Test
	void unaVezEnviadosCajaNoAnulaPagos() {
		Liquidacion liquidacion = conUnPago();
		ReflectionTestUtils.setField(liquidacion.getPagos().getFirst(), "id", 9L);
		liquidacion.enviar(CAJERA, AHORA);

		assertThatThrownBy(() -> liquidacion.anularPago(9L, CAJERA, AHORA)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void cajaEnviaLosPagosAContabilidad() {
		Liquidacion liquidacion = conUnPago();

		liquidacion.enviar(CAJERA, AHORA);

		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.ENVIADA);
		assertThat(liquidacion.getEnviadaPorId()).isEqualTo(CAJERA);
		assertThat(liquidacion.getEnviadaEn()).isEqualTo(AHORA);
	}

	@Test
	void noSeEnviaSinPagos() {
		Liquidacion liquidacion = Liquidacion.abrir(enPago());

		assertThatThrownBy(() -> liquidacion.enviar(CAJERA, AHORA)).isInstanceOf(ConflictoException.class)
				.hasMessage("Registra al menos un pago antes de enviarlos a Contabilidad");
	}

	@Test
	void unaVezEnviadosCajaYaNoCambiaLosPagos() {
		Liquidacion liquidacion = conUnPago();
		liquidacion.enviar(CAJERA, AHORA);

		assertThatThrownBy(() -> liquidacion.registrarPago(BigDecimal.TEN, HOY, null, CAJERA, HOY))
				.isInstanceOf(ConflictoException.class)
				.hasMessage("Los pagos ya se enviaron a Contabilidad y no se pueden cambiar");
		assertThatThrownBy(() -> liquidacion.enviar(CAJERA, AHORA)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void contabilidadValidaYElProspectoPasaAFacturacionDesdeElErp() {
		Liquidacion liquidacion = conUnPago();
		liquidacion.enviar(CAJERA, AHORA);

		liquidacion.validar(CONTADOR, AHORA);

		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.VALIDADA);
		assertThat(liquidacion.getResueltaPorId()).isEqualTo(CONTADOR);
		assertThat(liquidacion.getProspecto().getEtapa()).isEqualTo(Etapa.FACTURACION);
		assertThat(liquidacion.getProspecto().getHistorial()).last().satisfies(movimiento -> {
			assertThat(movimiento.getOrigen()).isEqualTo(OrigenCambio.ERP);
			assertThat(movimiento.getUsuarioId()).isEqualTo(CONTADOR);
		});
	}

	@Test
	void soloSeValidaLoQueCajaYaEnvio() {
		Liquidacion liquidacion = conUnPago();

		assertThatThrownBy(() -> liquidacion.validar(CONTADOR, AHORA)).isInstanceOf(ConflictoException.class)
				.hasMessage("Contabilidad solo revisa pagos que Caja ya envió");
		assertThat(liquidacion.getProspecto().getEtapa()).isEqualTo(Etapa.PAGO);
	}

	@Test
	void contabilidadRechazaConMotivoYCajaCorrigeYVuelveAEnviar() {
		Liquidacion liquidacion = conUnPago();
		liquidacion.enviar(CAJERA, AHORA);

		liquidacion.rechazar("  Falta el comprobante del enganche ", CONTADOR, AHORA);

		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.RECHAZADA);
		assertThat(liquidacion.getMotivoRechazo()).isEqualTo("Falta el comprobante del enganche");
		assertThat(liquidacion.getProspecto().getEtapa()).isEqualTo(Etapa.PAGO);

		liquidacion.registrarPago(new BigDecimal("1000"), HOY, null, CAJERA, HOY);
		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.ABIERTA);
		liquidacion.enviar(CAJERA, AHORA);
		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.ENVIADA);
		assertThat(liquidacion.getMotivoRechazo()).isNull();
	}

	@Test
	void rechazarExigeMotivo() {
		Liquidacion liquidacion = conUnPago();
		liquidacion.enviar(CAJERA, AHORA);

		assertThatThrownBy(() -> liquidacion.rechazar(" ", CONTADOR, AHORA))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Escribe por qué se rechazan los pagos");
		assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.ENVIADA);
	}
}
