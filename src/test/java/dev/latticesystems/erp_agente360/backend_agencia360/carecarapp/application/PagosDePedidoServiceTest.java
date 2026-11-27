package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MetodoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCancelacionPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.MetodoPagoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PagoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PedidoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ProcesadorSimulado;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pagar un pedido de la tienda con el cobro de las órdenes (V2-M14.04), con el
 * procesador simulado real y lo demás de mentira.
 */
class PagosDePedidoServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
	private static final Long CLIENTE = 3L;
	private static final Long PEDIDO = 15L;

	private final PagoAppRepository pagos = mock(PagoAppRepository.class);
	private final PedidoAppRepository pedidos = mock(PedidoAppRepository.class);
	private final MetodoPagoRepository metodos = mock(MetodoPagoRepository.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private final List<Object> publicados = new ArrayList<>();
	private final AtomicLong ids = new AtomicLong(200);

	private PagosAppService servicio;
	private PedidoApp pedido;

	@BeforeEach
	void preparar() {
		servicio = new PagosAppService(pagos, metodos, mock(PerfilFiscalRepository.class), new ProcesadorSimulado(),
				mock(HistorialDeServicios.class), mock(TallerEnVivo.class), mock(VehiculosDeClientes.class), pedidos,
				eventos, Clock.fixed(AHORA, ZoneOffset.UTC), ZoneId.of("America/Mexico_City"));
		pedido = pedido(AHORA.plus(Duration.ofHours(48)));
		when(pedidos.existsByIdAndClienteId(PEDIDO, CLIENTE)).thenReturn(true);
		when(pedidos.bloquear(PEDIDO)).thenAnswer(invocacion -> Optional.of(pedido));
		when(pagos.saveAndFlush(any(PagoApp.class))).thenAnswer(invocacion -> conId(invocacion.getArgument(0)));
		when(metodos.saveAndFlush(any(MetodoPago.class))).thenAnswer(invocacion -> conId(invocacion.getArgument(0)));
		doAnswer(invocacion -> publicados.add(invocacion.getArgument(0))).when(eventos).publishEvent(any(Object.class));
	}

	@Test
	void pagaElTotalDelPedidoLoMarcaPagadoYAvisaConElPedido() {
		PagosAppService.Cobro cobro = servicio.pagarPedido(CLIENTE, PEDIDO, "llave-1234", tarjeta("tok_sim_visa"));

		assertThat(cobro.creado()).isTrue();
		assertThat(cobro.pago().estado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(cobro.pago().pedidoId()).isEqualTo(PEDIDO);
		assertThat(cobro.pago().ordenId()).isNull();
		assertThat(cobro.pago().vehiculoId()).isNull();
		assertThat(cobro.pago().monto()).isEqualByComparingTo("201.00");
		assertThat(pedido.getPagadoEn()).isEqualTo(AHORA);
		PagoConfirmado confirmado = (PagoConfirmado) publicados.getFirst();
		assertThat(confirmado.pedidoId()).isEqualTo(PEDIDO);
		assertThat(confirmado.ordenId()).isNull();
		AvisoAlCliente aviso = (AvisoAlCliente) publicados.get(1);
		assertThat(aviso.evento()).isEqualTo("pago.confirmado");
		assertThat(aviso.datos()).containsEntry("pedidoId", PEDIDO).doesNotContainKey("ordenId");
		verify(pagos, never()).bloquear(anyLong());
	}

	@Test
	void laMismaLlaveDevuelveElIntentoYConOtroObjetoEsInvalida() {
		PagoApp previo = PagoApp.conTarjeta(PagoApp.Cobrable.dePedido(PEDIDO, CLIENTE, new BigDecimal("201.00")),
				"SIMULADO", "sim_pi_1", "llave-1234", TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow(),
				YearMonth.of(2028, 1), null, false, null, AHORA);
		when(pagos.findByClienteIdAndLlaveIdempotencia(CLIENTE, "llave-1234")).thenReturn(Optional.of(previo));

		assertThat(servicio.pagarPedido(CLIENTE, PEDIDO, "llave-1234", tarjeta("tok_sim_visa")).creado()).isFalse();
		verify(pagos, never()).saveAndFlush(any());

		when(pedidos.existsByIdAndClienteId(16L, CLIENTE)).thenReturn(true);
		when(pedidos.bloquear(16L)).thenReturn(Optional.of(pedido(AHORA.plusSeconds(60))));
		assertThatThrownBy(() -> servicio.pagarPedido(CLIENTE, 16L, "llave-1234", tarjeta("tok_sim_visa")))
				.isInstanceOf(IllegalArgumentException.class);
		// La llave de un pedido tampoco sirve para una orden.
		assertThatThrownBy(() -> servicio.pagar(CLIENTE, 41L, "llave-1234", tarjeta("tok_sim_visa")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unPedidoAjenoNoExiste() {
		assertThatThrownBy(() -> servicio.pagarPedido(99L, PEDIDO, "llave-1234", tarjeta("tok_sim_visa")))
				.isInstanceOf(RecursoNoEncontradoException.class);
		verify(pagos, never()).saveAndFlush(any());
	}

	@Test
	void noSePagaVencidoCanceladoSinTotalNiPagado() {
		pedido = pedido(AHORA);
		noPagable();

		pedido = pedido(AHORA.plusSeconds(60));
		pedido.cancelar(MotivoCancelacionPedido.CLIENTE, AHORA);
		noPagable();

		pedido = PedidoApp.apartar(CLIENTE, "llave-pedido", null, AHORA.plusSeconds(60));
		noPagable();

		pedido = pedido(AHORA.plusSeconds(60));
		pedido.marcarPagado(AHORA);
		noPagable();
		verify(pagos, never()).saveAndFlush(any());
	}

	@Test
	void elTresDeSecureDejaElPedidoSinPagarYSinAviso() {
		PagoVista pendiente = servicio.pagarPedido(CLIENTE, PEDIDO, "llave-1234", tarjeta("tok_sim_3ds")).pago();

		assertThat(pendiente.estado()).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(pendiente.requiereAutenticacion()).isTrue();
		assertThat(pedido.getPagadoEn()).isNull();
		assertThat(publicados).isEmpty();
	}

	@Test
	void unRechazoNoMarcaElPedido() {
		PagoVista rechazo = servicio.pagarPedido(CLIENTE, PEDIDO, "llave-1234", tarjeta("tok_sim_fondos_insuficientes"))
				.pago();

		assertThat(rechazo.estado()).isEqualTo(EstadoPago.FALLIDO);
		assertThat(pedido.getPagadoEn()).isNull();
		assertThat(pedido.pagable(AHORA)).isTrue();
	}

	@Test
	void elTresDeSecureDeUnPedidoSeRespondeEnLaMismaRuta() {
		PagoApp pendiente = PagoApp.conTarjeta(PagoApp.Cobrable.dePedido(PEDIDO, CLIENTE, new BigDecimal("201.00")),
				"SIMULADO", "sim_pi_2", "otra-llave", TarjetaDePrueba.deToken("tok_sim_3ds").orElseThrow(),
				YearMonth.of(2028, 1), null, false, null, AHORA.minus(Duration.ofMinutes(5)));
		pendiente.pedirAutenticacion(AHORA.minus(Duration.ofMinutes(5)));
		conId(pendiente);
		when(pagos.findFirstByPedidoIdAndEstado(PEDIDO, EstadoPago.PENDIENTE)).thenReturn(Optional.of(pendiente));
		assertThatThrownBy(() -> servicio.pagarPedido(CLIENTE, PEDIDO, "llave-1234", tarjeta("tok_sim_visa")))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.PAGO_EN_CURSO);

		when(pagos.ordenDelPago(pendiente.getId(), CLIENTE)).thenReturn(Optional.empty());
		when(pagos.pedidoDelPago(pendiente.getId(), CLIENTE)).thenReturn(Optional.of(PEDIDO));
		when(pagos.findByIdAndClienteId(pendiente.getId(), CLIENTE)).thenReturn(Optional.of(pendiente));
		// Aunque su fecha ya pasó, el intento vigente detiene el vencimiento.
		pedido = pedido(AHORA.minusSeconds(60));

		PagoVista pagado = servicio.autenticar(CLIENTE, pendiente.getId(), true);

		assertThat(pagado.estado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(pedido.getPagadoEn()).isEqualTo(AHORA);
		verify(pedidos, atLeastOnce()).bloquear(PEDIDO);
		verify(pagos, never()).bloquear(anyLong());
		assertThat(publicados).hasSize(2);
	}

	@Test
	void unPagoQueNoEsMioNoSeAutentica() {
		when(pagos.ordenDelPago(5L, CLIENTE)).thenReturn(Optional.empty());
		when(pagos.pedidoDelPago(5L, CLIENTE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.autenticar(CLIENTE, 5L, true))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	private void noPagable() {
		assertThatThrownBy(
				() -> servicio.pagarPedido(CLIENTE, PEDIDO, "llave-" + ids.incrementAndGet(), tarjeta("tok_sim_visa")))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.PEDIDO_NO_PAGABLE);
	}

	private static PedidoApp pedido(Instant venceEn) {
		PedidoApp nuevo = PedidoApp.apartar(CLIENTE, "llave-pedido", null, venceEn);
		nuevo.agregar(7L, 2, new BigDecimal("100.50"));
		ReflectionTestUtils.setField(nuevo, "id", PEDIDO);
		return nuevo;
	}

	private static NuevoPago tarjeta(String token) {
		return new NuevoPago(token, 12, 2028, null, false, null);
	}

	/** Como la base: guardar le da id a lo que aún no lo tiene. */
	private <T> T conId(T entidad) {
		if (ReflectionTestUtils.getField(entidad, "id") == null) {
			ReflectionTestUtils.setField(entidad, "id", ids.incrementAndGet());
		}
		return entidad;
	}
}
