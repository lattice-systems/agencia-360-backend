package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EntregaPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCancelacionPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PedidoAppTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T12:00:00Z");
	private static final Duration DOS_DIAS = Duration.ofHours(48);

	private static PedidoApp pedido(Instant venceEn) {
		PedidoApp pedido = PedidoApp.apartar(1L, "llave-de-prueba", null, venceEn);
		pedido.agregar(7L, 2, new BigDecimal("100.50"));
		return pedido;
	}

	@Test
	void sumaElTotalYSabeCuandoVence() {
		PedidoApp pedido = pedido(AHORA);

		assertThat(pedido.getTotal()).isEqualByComparingTo("201.00");
		assertThat(pedido.getEntrega()).isEqualTo(EntregaPedido.MOSTRADOR);
		assertThat(pedido.vencido(AHORA)).isTrue();
		assertThat(pedido.vencido(AHORA.minusSeconds(1))).isFalse();
	}

	@Test
	void alQuedarListoDaAlMenosElPlazoParaRecogerSinAcortarElQueTenia() {
		PedidoApp corto = pedido(AHORA.plusSeconds(60));
		PedidoApp largo = pedido(AHORA.plus(Duration.ofDays(9)));

		corto.marcarListo(AHORA, DOS_DIAS);
		largo.marcarListo(AHORA, DOS_DIAS);

		assertThat(corto.getVenceEn()).isEqualTo(AHORA.plus(DOS_DIAS));
		assertThat(largo.getVenceEn()).isEqualTo(AHORA.plus(Duration.ofDays(9)));
	}

	@Test
	void soloSeEntregaListoYUnPedidoCerradoNoCambia() {
		PedidoApp pedido = pedido(AHORA.plus(DOS_DIAS));
		assertThatThrownBy(() -> pedido.entregar(AHORA)).isInstanceOf(ConflictoException.class);

		pedido.marcarListo(AHORA, DOS_DIAS);
		pedido.entregar(AHORA);

		assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(pedido.getVenceEn()).isNull();
		assertThatThrownBy(() -> pedido.cancelar(MotivoCancelacionPedido.CLIENTE, AHORA))
				.isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> pedido.instalarEn(null, AHORA)).isInstanceOf(ConflictoException.class);
	}
}
