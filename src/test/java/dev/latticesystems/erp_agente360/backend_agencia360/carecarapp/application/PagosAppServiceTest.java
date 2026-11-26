package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MetodoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoFalloPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.MetodoPagoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PagoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ProcesadorSimulado;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.DetalleDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.PasoDeEtapa;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Reglas del cobro simulado (V2-M08.02 y V2-M08.04) con el procesador simulado
 * real y lo demás de mentira.
 */
class PagosAppServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T18:00:00Z");
	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final Long CLIENTE = 3L;
	private static final Long ORDEN = 41L;
	private static final Long VEHICULO = 9L;

	private final PagoAppRepository pagos = mock(PagoAppRepository.class);
	private final MetodoPagoRepository metodos = mock(MetodoPagoRepository.class);
	private final PerfilFiscalRepository perfiles = mock(PerfilFiscalRepository.class);
	private final HistorialDeServicios historial = mock(HistorialDeServicios.class);
	private final TallerEnVivo taller = mock(TallerEnVivo.class);
	private final VehiculosDeClientes vehiculos = mock(VehiculosDeClientes.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private final List<Object> publicados = new ArrayList<>();

	private PagosAppService servicio;

	@BeforeEach
	void preparar() {
		servicio = new PagosAppService(pagos, metodos, perfiles, new ProcesadorSimulado(), historial, taller, vehiculos,
				eventos, Clock.fixed(AHORA, ZoneOffset.UTC), ZONA);
		when(pagos.saveAndFlush(any(PagoApp.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		when(metodos.saveAndFlush(any(MetodoPago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		when(vehiculos.buscarPorId(VEHICULO)).thenReturn(Optional.of(new VehiculoDeCliente(VEHICULO, "VIN", "Nissan",
				"March", 2026, null, null, null, null, new Propietario(CLIENTE, "Paula", null, null))));
		when(historial.detalle(ORDEN)).thenReturn(Optional.of(detalle(EstatusOrden.TERMINADO, "1650.00")));
		when(taller.orden(ORDEN)).thenReturn(Optional.of(delTaller(true)));
		doAnswer(invocacion -> publicados.add(invocacion.getArgument(0))).when(eventos).publishEvent(any(Object.class));
	}

	@Test
	void unaTarjetaAprobadaPagaYAvisa() {
		PagosAppService.Cobro cobro = servicio.pagar(CLIENTE, ORDEN, "llave-1234", tarjeta("tok_sim_visa", false));

		assertThat(cobro.creado()).isTrue();
		assertThat(cobro.pago().estado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(cobro.pago().monto()).isEqualByComparingTo("1650.00");
		assertThat(cobro.pago().simulado()).isTrue();
		assertThat(publicados).hasSize(2);
		assertThat(publicados.getFirst()).isInstanceOf(PagoConfirmado.class);
		assertThat(((AvisoAlCliente) publicados.get(1)).evento()).isEqualTo("pago.confirmado");
		verify(pagos).bloquear(anyLong());
	}

	@Test
	void laMismaLlaveDevuelveElMismoIntentoSinCobrar() {
		PagoApp previo = PagoApp.conTarjeta(new PagoApp.Cobrable(ORDEN, VEHICULO, CLIENTE, new BigDecimal("1650.00")),
				"SIMULADO", "sim_pi_1", "llave-1234", TarjetaDePrueba.deToken("tok_sim_visa").orElseThrow(),
				YearMonth.of(2028, 1), null, false, null, AHORA);
		when(pagos.findByClienteIdAndLlaveIdempotencia(CLIENTE, "llave-1234")).thenReturn(Optional.of(previo));

		PagosAppService.Cobro cobro = servicio.pagar(CLIENTE, ORDEN, " llave-1234 ", tarjeta("tok_sim_visa", false));

		assertThat(cobro.creado()).isFalse();
		verify(pagos, never()).saveAndFlush(any());
		assertThat(publicados).isEmpty();
		assertThatThrownBy(() -> servicio.pagar(CLIENTE, 77L, "llave-1234", tarjeta("tok_sim_visa", false)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fondosInsuficientesQuedaFallidoSinAvisar() {
		PagoVista pago = servicio.pagar(CLIENTE, ORDEN, "llave-1234", tarjeta("tok_sim_fondos_insuficientes", true))
				.pago();

		assertThat(pago.estado()).isEqualTo(EstadoPago.FALLIDO);
		assertThat(pago.motivoFallo()).isEqualTo(MotivoFalloPago.FONDOS_INSUFICIENTES);
		assertThat(publicados).isEmpty();
		verify(metodos, never()).saveAndFlush(any());
	}

	@Test
	void unIntentoAbiertoBloqueaHastaQueVence() {
		PagoApp abierto = pendiente(AHORA.minus(Duration.ofMinutes(5)));
		when(pagos.findFirstByOrdenIdAndEstado(ORDEN, EstadoPago.PENDIENTE)).thenReturn(Optional.of(abierto));

		assertThatThrownBy(() -> servicio.pagar(CLIENTE, ORDEN, "llave-1234", tarjeta("tok_sim_visa", false)))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.PAGO_EN_CURSO);

		PagoApp olvidado = pendiente(AHORA.minus(Duration.ofMinutes(16)));
		when(pagos.findFirstByOrdenIdAndEstado(ORDEN, EstadoPago.PENDIENTE)).thenReturn(Optional.of(olvidado));
		assertThat(servicio.pagar(CLIENTE, ORDEN, "llave-5678", tarjeta("tok_sim_visa", false)).pago().estado())
				.isEqualTo(EstadoPago.PAGADO);
		assertThat(olvidado.getMotivoFallo()).isEqualTo(MotivoFalloPago.AUTENTICACION_VENCIDA);
	}

	@Test
	void laPrimeraTarjetaGuardadaQuedaPredeterminadaYNoPasaDeDiez() {
		when(metodos.countByClienteId(CLIENTE)).thenReturn(0L);
		servicio.pagar(CLIENTE, ORDEN, "llave-1234", tarjeta("tok_sim_mastercard", true));
		ArgumentCaptor<MetodoPago> guardada = ArgumentCaptor.forClass(MetodoPago.class);
		verify(metodos).saveAndFlush(guardada.capture());
		assertThat(guardada.getValue().isPredeterminado()).isTrue();
		assertThat(guardada.getValue().getUltimos4()).isEqualTo("4444");
		assertThat(guardada.getValue().getTokenProcesador()).isEqualTo("tok_sim_mastercard");

		when(metodos.countByClienteId(CLIENTE)).thenReturn((long) PagosAppService.TARJETAS_MAXIMAS);
		when(historial.detalle(42L)).thenReturn(Optional.of(detalle(42L, EstatusOrden.TERMINADO, "100.00")));
		when(taller.orden(42L)).thenReturn(Optional.of(delTaller(true)));
		PagoVista sinGuardar = servicio.pagar(CLIENTE, 42L, "llave-5678", tarjeta("tok_sim_visa", true)).pago();
		assertThat(sinGuardar.estado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(sinGuardar.metodoPagoId()).isNull();
	}

	@Test
	void noSePagaLoQueNoEstaListoNiLoRegistradoNiLoAjeno() {
		when(historial.detalle(ORDEN)).thenReturn(Optional.of(detalle(EstatusOrden.EN_PROCESO, "1650.00")));
		assertThatThrownBy(() -> servicio.pagar(CLIENTE, ORDEN, "llave-1234", tarjeta("tok_sim_visa", false)))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.ORDEN_NO_PAGABLE);

		when(historial.detalle(ORDEN)).thenReturn(Optional.of(detalle(EstatusOrden.ENTREGADO, "1650.00")));
		when(taller.orden(ORDEN)).thenReturn(Optional.of(delTaller(false)));
		assertThatThrownBy(() -> servicio.pagar(CLIENTE, ORDEN, "llave-1234", tarjeta("tok_sim_visa", false)))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.ORDEN_NO_PAGABLE);

		assertThatThrownBy(() -> servicio.pagar(99L, ORDEN, "llave-1234", tarjeta("tok_sim_visa", false)))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unaTarjetaVencidaODesconocidaNoSeCobra() {
		assertThatThrownBy(() -> servicio.pagar(CLIENTE, ORDEN, "llave-1234",
				new NuevoPago("tok_sim_visa", 10, 2026, null, false, null)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("venció");
		assertThat(
				servicio.pagar(CLIENTE, ORDEN, "llave-1234", new NuevoPago("tok_sim_visa", 11, 2026, null, false, null))
						.pago().estado())
				.isEqualTo(EstadoPago.PAGADO);
		assertThatThrownBy(() -> servicio.pagar(CLIENTE, ORDEN, "llave-5678", tarjeta("4242424242424242", false)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elTicketDiceSiSePuedePagar() {
		DetalleDelServicio lista = detalle(EstatusOrden.TERMINADO, "1650.00");
		assertThat(servicio.cobroDe(lista).pagable()).isTrue();
		assertThat(servicio.cobroDe(detalle(EstatusOrden.TERMINADO, "0.00")).pagable()).isFalse();

		PagoApp pagado = pendiente(AHORA);
		pagado.aprobar(AHORA);
		when(pagos.findFirstByOrdenIdAndEstado(eq(ORDEN), eq(EstadoPago.PAGADO))).thenReturn(Optional.of(pagado));
		CobroDeOrden cobro = servicio.cobroDe(lista);
		assertThat(cobro.pagable()).isFalse();
		assertThat(cobro.pago().estado()).isEqualTo(EstadoPago.PAGADO);
	}

	private static NuevoPago tarjeta(String token, boolean guardar) {
		return new NuevoPago(token, 12, 2028, null, guardar, null);
	}

	private static PagoApp pendiente(Instant creado) {
		PagoApp pago = PagoApp.conTarjeta(new PagoApp.Cobrable(ORDEN, VEHICULO, CLIENTE, new BigDecimal("1650.00")),
				"SIMULADO", "sim_pi_x", "otra-llave", TarjetaDePrueba.deToken("tok_sim_3ds").orElseThrow(),
				YearMonth.of(2028, 1), null, false, null, creado);
		pago.pedirAutenticacion(creado);
		return pago;
	}

	private static DetalleDelServicio detalle(EstatusOrden estatus, String total) {
		return detalle(ORDEN, estatus, total);
	}

	private static DetalleDelServicio detalle(Long id, EstatusOrden estatus, String total) {
		BigDecimal monto = new BigDecimal(total);
		return new DetalleDelServicio(id, VEHICULO, LocalDate.of(2026, 11, 26), 15200, estatus, AHORA, List.of(),
				List.of(), monto, BigDecimal.ZERO, monto, BigDecimal.ZERO, monto);
	}

	private static OrdenEnVivo delTaller(boolean conEtapas) {
		List<PasoDeEtapa> etapas = conEtapas
				? List.of(new PasoDeEtapa(EtapaTaller.RECIBIDO, AHORA, null, null))
				: List.of();
		return new OrdenEnVivo(ORDEN, VEHICULO, EstatusOrden.TERMINADO, EtapaTaller.LISTO_PARA_ENTREGA, null, null, 86,
				etapas, List.of(), List.of(), List.of(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.TEN, List.of());
	}
}
