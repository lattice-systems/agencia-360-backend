package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PagoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PedidoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ApartadoDeRefacciones;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ProductoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Lo que cambia en el pedido de la tienda cuando se paga en la app (V2-M14.04):
 * ya no vence, un intento abierto detiene el vencimiento, no se cancela desde
 * la app y la vista dice si se puede pagar y con qué se pagó.
 */
class TiendaPedidoPagadoTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
	private static final Long CLIENTE = 3L;
	private static final Long PEDIDO = 15L;
	private static final Long PIEZA = 7L;

	private final PedidoAppRepository pedidos = mock(PedidoAppRepository.class);
	private final PagoAppRepository pagos = mock(PagoAppRepository.class);
	private final CatalogoDeTienda catalogo = mock(CatalogoDeTienda.class);
	private final ApartadoDeRefacciones inventario = mock(ApartadoDeRefacciones.class);

	private TiendaAppService tienda;
	private PedidoApp pedido;

	@BeforeEach
	void preparar() {
		tienda = new TiendaAppService(pedidos, mock(CitaRepository.class), mock(VehiculosDeClientes.class), catalogo,
				inventario, pagos, mock(ApplicationEventPublisher.class), Duration.ofHours(48),
				Clock.fixed(AHORA, ZoneOffset.UTC));
		when(pedidos.existsByIdAndClienteId(PEDIDO, CLIENTE)).thenReturn(true);
		when(pedidos.bloquear(PEDIDO)).thenAnswer(invocacion -> Optional.of(pedido));
		when(catalogo.porIds(any())).thenReturn(List.of(new ProductoDeTienda(PIEZA, "TAP-1", "Tapetes", null,
				CategoriaTienda.ACCESORIO, new BigDecimal("100.50"), null, 3, true, List.of())));
	}

	@Test
	void unPedidoAbiertoEsPagableYSinPago() {
		pedido = pedido(AHORA.plus(Duration.ofHours(48)));

		PedidoVista vista = tienda.ver(CLIENTE, PEDIDO);

		assertThat(vista.pagable()).isTrue();
		assertThat(vista.pago()).isNull();
		assertThat(vista.pagadoEn()).isNull();
	}

	@Test
	void pagadoYaNoVenceYTraeSuPago() {
		pedido = pedido(AHORA.minusSeconds(60));
		pedido.marcarPagado(AHORA.minus(Duration.ofHours(1)));
		PagoApp pagado = PagoApp.conTarjeta(PagoApp.Cobrable.dePedido(PEDIDO, CLIENTE, new BigDecimal("201.00")),
				"SIMULADO", "sim_pi_1", "llave-1234", TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow(),
				YearMonth.of(2028, 1), null, false, null, AHORA);
		pagado.aprobar(AHORA.minus(Duration.ofHours(1)));
		when(pagos.findFirstByPedidoIdAndEstado(PEDIDO, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado));

		PedidoVista vista = tienda.ver(CLIENTE, PEDIDO);

		assertThat(vista.estado()).isEqualTo("apartado");
		assertThat(vista.pagable()).isFalse();
		assertThat(vista.pagadoEn()).isEqualTo(AHORA.minus(Duration.ofHours(1)));
		assertThat(vista.pago().ultimos4()).isEqualTo("4242");
		verify(inventario, never()).liberar(any(), anyMap());
	}

	@Test
	void unIntentoQueEsperaElTresDeSecureDetieneElVencimiento() {
		pedido = pedido(AHORA.minusSeconds(60));
		when(pagos.existsByPedidoIdAndEstadoAndCreadoEnAfter(PEDIDO, EstadoPago.PENDIENTE,
				AHORA.minus(PagosAppService.VIGENCIA_AUTENTICACION))).thenReturn(true);

		assertThat(tienda.ver(CLIENTE, PEDIDO).estado()).isEqualTo("apartado");
		verify(inventario, never()).liberar(any(), anyMap());

		when(pagos.existsByPedidoIdAndEstadoAndCreadoEnAfter(eq(PEDIDO), eq(EstadoPago.PENDIENTE), any()))
				.thenReturn(false);
		assertThat(tienda.ver(CLIENTE, PEDIDO).estado()).isEqualTo("cancelado");
		verify(inventario).liberar(eq(PEDIDO), anyMap());
	}

	@Test
	void pagadoNoSeCancelaDesdeLaAppPeroCambiaDeInstalacion() {
		pedido = pedido(AHORA.plus(Duration.ofHours(48)));
		pedido.marcarPagado(AHORA);

		assertThatThrownBy(() -> tienda.cancelar(CLIENTE, PEDIDO)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo(CodigosError.PEDIDO_PAGADO);
		assertThat(tienda.instalar(CLIENTE, PEDIDO, null).entrega()).isEqualTo("mostrador");
		assertThat(tienda.cancelarPorLaAgencia(PEDIDO).estado()).isEqualTo("cancelado");
	}

	@Test
	void conUnIntentoAbiertoNoSeCancelaDesdeLaApp() {
		pedido = pedido(AHORA.plus(Duration.ofHours(48)));
		when(pagos.existsByPedidoIdAndEstadoAndCreadoEnAfter(eq(PEDIDO), eq(EstadoPago.PENDIENTE), any()))
				.thenReturn(true);

		assertThatThrownBy(() -> tienda.cancelar(CLIENTE, PEDIDO)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo(CodigosError.PAGO_EN_CURSO);
		verify(inventario, never()).liberar(any(), anyMap());
	}

	@Test
	void elVencimientoProgramadoSaltaLosPagadosYLosQueEsperanElBanco() {
		when(pedidos.idsVencidos(PedidoApp.estadosAbiertos(), AHORA,
				AHORA.minus(PagosAppService.VIGENCIA_AUTENTICACION))).thenReturn(List.of(PEDIDO));

		assertThat(tienda.idsVencidos()).containsExactly(PEDIDO);
	}

	private static PedidoApp pedido(Instant venceEn) {
		PedidoApp nuevo = PedidoApp.apartar(CLIENTE, "llave-pedido", null, venceEn);
		nuevo.agregar(PIEZA, 2, new BigDecimal("100.50"));
		ReflectionTestUtils.setField(nuevo, "id", PEDIDO);
		return nuevo;
	}
}
