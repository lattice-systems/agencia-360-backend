package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoReferido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoPuntos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Referido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ReferidoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeReferidoCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ReferidoRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos.EstadoProspecto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ReferidosAppServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");

	private final ReferidoRepository referidos = mock(ReferidoRepository.class);
	private final DirectorioClientes directorio = mock(DirectorioClientes.class);
	private final ConsultaProspectos prospectos = mock(ConsultaProspectos.class);
	private final CuentaPuntosService cuenta = mock(CuentaPuntosService.class);
	private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	private ReferidosAppService servicio;

	@BeforeEach
	void preparar() {
		servicio = new ReferidosAppService(referidos, directorio, prospectos, cuenta, ReglasLealtad.porOmision(),
				eventos, Clock.fixed(AHORA, ZoneOffset.UTC));
		when(referidos.save(any(Referido.class))).thenAnswer(invocacion -> {
			Referido referido = invocacion.getArgument(0);
			ReflectionTestUtils.setField(referido, "id", 70L);
			return referido;
		});
	}

	@Test
	void elCodigoDelClienteEsCcYSuIdEnBase36() {
		assertThat(ReferidosAppService.codigoDe(1L)).isEqualTo("CC00001");
		assertThat(ReferidosAppService.codigoDe(35L)).isEqualTo("CC0000Z");
		assertThat(ReferidosAppService.codigoDe(60_466_176L)).isEqualTo("CC100000");
	}

	@Test
	void referirNormalizaElTelefonoYAvisaAVentas() {
		ReferidoApp referido = servicio.referir(3L, "Luis Pérez", "+52 442-123-4567", " Luis@Correo.com ");

		assertThat(referido.estado()).isEqualTo(EstadoReferido.REGISTRADO);
		assertThat(referido.puntos()).isZero();
		ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
		verify(eventos).publishEvent(evento.capture());
		assertThat(evento.getValue())
				.isEqualTo(new ReferidoRegistrado(70L, "Luis Pérez", "4421234567", "luis@correo.com"));
	}

	@Test
	void unTelefonoQueNoTieneDiezDigitosSeRechaza() {
		assertThatThrownBy(() -> servicio.referir(3L, "Luis", "44212", null))
				.isInstanceOf(IllegalArgumentException.class);
		verify(referidos, never()).save(any());
	}

	@Test
	void siYaEsClienteOYaLoRefirieronEsDuplicado() {
		when(directorio.buscarPorCorreo("ana@correo.com")).thenReturn(Optional.of(new ClienteRegistrado(5L, "Ana",
				"ana@correo.com", "4771234567", Instant.parse("2024-03-01T10:00:00Z"))));
		when(referidos.existsByTelefono("4421234567")).thenReturn(true);

		assertThatThrownBy(() -> servicio.referir(3L, "Ana", null, "ana@correo.com"))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.REFERIDO_DUPLICADO);
		assertThatThrownBy(() -> servicio.referir(3L, "Luis", "4421234567", null))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.REFERIDO_DUPLICADO);
		verify(eventos, never()).publishEvent(any());
	}

	@Test
	void conDiezAbiertosNoSePuedeReferirOtro() {
		when(referidos.countByClienteIdAndEstadoIn(any(), any())).thenReturn(10L);
		assertThatThrownBy(() -> servicio.referir(3L, "Luis", "4421234567", null))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.LIMITE_REFERIDOS);
	}

	@Test
	void alCrearseElProspectoPasaASeguimientoYAlFinalizarDaLosPuntosUnaVez() {
		Referido referido = new Referido(3L, "CC00003", "Luis", "4421234567", null, AHORA);
		ReflectionTestUtils.setField(referido, "id", 70L);
		when(referidos.findById(70L)).thenReturn(Optional.of(referido));
		when(referidos.findByProspectoId(40L)).thenReturn(Optional.of(referido));

		servicio.alCrearseProspecto(new ProspectoDeReferidoCreado(70L, 40L));
		assertThat(referido.getEstado()).isEqualTo(EstadoReferido.EN_SEGUIMIENTO);
		assertThat(referido.getProspectoId()).isEqualTo(40L);

		ProspectoFinalizado finalizado = new ProspectoFinalizado(40L, 8L, "Luis", null, AHORA);
		servicio.alFinalizarProspecto(finalizado);
		servicio.alFinalizarProspecto(finalizado);

		assertThat(referido.getEstado()).isEqualTo(EstadoReferido.COMPRO);
		verify(cuenta, times(1)).mover(3L, 500, MotivoPuntos.REFERIDO, "REFERIDO", 70L);
	}

	@Test
	void unaVentaFinalizadaQueNoEsDeUnReferidoNoDaPuntos() {
		servicio.alFinalizarProspecto(new ProspectoFinalizado(41L, 8L, "Otro", null, AHORA));
		verify(cuenta, never()).mover(any(), anyInt(), any(), any(), any());
	}

	@Test
	void unProspectoCerradoSinVentaSeVeDescartado() {
		Referido cerrado = new Referido(3L, "CC00003", "Luis", "4421234567", null, AHORA);
		cerrado.ligarProspecto(40L);
		Referido vivo = new Referido(3L, "CC00003", "Mara", null, "mara@correo.com", AHORA);
		vivo.ligarProspecto(41L);
		when(referidos.findByClienteIdOrderByCreadoEnDescIdDesc(3L)).thenReturn(List.of(cerrado, vivo));
		when(prospectos.estado(40L)).thenReturn(Optional.of(new EstadoProspecto("CERRADO", true, 8L)));
		when(prospectos.estado(41L)).thenReturn(Optional.of(new EstadoProspecto("VISITA", false, 8L)));

		List<ReferidoApp> lista = servicio.referidos(3L);

		assertThat(lista).extracting(ReferidoApp::estado).containsExactly(EstadoReferido.DESCARTADO,
				EstadoReferido.EN_SEGUIMIENTO);
	}
}
