package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.DirectorioUsuarios;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.ArchivoGuardado;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.Propietario;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstatusOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.MotivoCambio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.OrdenServicioCambiada;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo.OrdenEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.EtapaOrden;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.OrdenServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TipoRenglon;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.TrabajoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.EtapaOrdenRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.EvidenciaOrdenRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.OrdenServicioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.PuntoInspeccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.infrastructure.TrabajoAdicionalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TallerServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-24T15:30:00Z");
	private static final ZonedDateTime AHORA_EN_LA_AGENCIA = AHORA.atZone(ExpedienteService.ZONA_AGENCIA);
	private static final byte[] FIRMA = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};
	private static final VehiculoDeCliente SENTRA = new VehiculoDeCliente(3L, "3N1AB8CV5RY000001", "Nissan", "Sentra",
			2026, "Advance", "ABC-123-D", 15_000, "Azul",
			new Propietario(9L, "Ana López", "ana@correo.com", "4775550101"));
	private static final UsuarioAutenticado ASESOR = new UsuarioAutenticado(7L, "asesor", Set.of(Role.SERVICIO));

	@Mock
	private OrdenServicioRepository ordenes;

	@Mock
	private EtapaOrdenRepository etapas;

	@Mock
	private PuntoInspeccionRepository puntos;

	@Mock
	private EvidenciaOrdenRepository evidencias;

	@Mock
	private TrabajoAdicionalRepository adicionales;

	@Mock
	private VehiculosDeClientes vehiculos;

	@Mock
	private DirectorioUsuarios directorioUsuarios;

	@Mock
	private AlmacenArchivos almacen;

	@Mock
	private ApplicationEventPublisher eventos;

	private TallerService service;

	@BeforeEach
	void setUp() {
		service = new TallerService(ordenes, etapas, puntos, evidencias, adicionales, vehiculos, directorioUsuarios,
				almacen, eventos, citaId -> java.util.List.of(), Clock.fixed(AHORA, ExpedienteService.ZONA_AGENCIA));
	}

	private static OrdenServicio orden(Long id) {
		OrdenServicio orden = OrdenServicio.abrir(3L, 15_000, 7L, null, null, AHORA_EN_LA_AGENCIA.minusHours(2));
		ReflectionTestUtils.setField(orden, "id", id);
		return orden;
	}

	private static TrabajoAdicional adicional(Long id, Long ordenId, String total) {
		TrabajoAdicional adicional = TrabajoAdicional.proponer(ordenId, "Balatas", BigDecimal.ZERO,
				new BigDecimal(total));
		ReflectionTestUtils.setField(adicional, "id", id);
		return adicional;
	}

	private void sinDetalle(Long ordenId) {
		when(evidencias.findByOrdenServicioIdOrderByIdAsc(ordenId)).thenReturn(List.of());
		when(puntos.findByOrdenServicioIdOrderByIdAsc(ordenId)).thenReturn(List.of());
		when(directorioUsuarios.nombresDe(List.of(7L))).thenReturn(Map.of(7L, "Laura Méndez"));
	}

	@Test
	void abreLaOrdenRecibidaConSuPrimeraEtapaYAvisa() {
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));
		when(ordenes.enCurso(List.of(3L), EstatusOrden.ENTREGADO)).thenReturn(List.of());
		when(ordenes.save(any(OrdenServicio.class))).thenAnswer(invocacion -> {
			OrdenServicio guardada = invocacion.getArgument(0);
			ReflectionTestUtils.setField(guardada, "id", 50L);
			return guardada;
		});
		List<EtapaOrden> guardadas = new ArrayList<>();
		when(etapas.save(any(EtapaOrden.class))).thenAnswer(invocacion -> {
			guardadas.add(invocacion.getArgument(0));
			return invocacion.getArgument(0);
		});
		when(etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(50L)).thenAnswer(invocacion -> guardadas);
		when(adicionales.findByOrdenServicioIdOrderByIdAsc(50L)).thenReturn(List.of());
		sinDetalle(50L);

		OrdenEnVivo abierta = service.abrir(3L, 15_200, null, LocalDateTime.of(2026, 11, 24, 18, 0), ASESOR);

		assertThat(abierta.estatus()).isEqualTo(EstatusOrden.RECIBIDO);
		assertThat(abierta.etapa()).isEqualTo(EtapaTaller.RECIBIDO);
		assertThat(abierta.etapas()).hasSize(1);
		assertThat(abierta.asesor()).isEqualTo("Laura Méndez");
		assertThat(abierta.entregaEstimada()).isEqualTo(Instant.parse("2026-11-25T00:00:00Z"));
		verify(eventos).publishEvent(new OrdenServicioCambiada(50L, 3L, 9L, EstatusOrden.RECIBIDO, EtapaTaller.RECIBIDO,
				MotivoCambio.APERTURA));
	}

	@Test
	void noAbreOtraOrdenSiElVehiculoTieneUnaSinEntregar() {
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));
		when(ordenes.enCurso(List.of(3L), EstatusOrden.ENTREGADO)).thenReturn(List.of(orden(1L)));

		assertThatThrownBy(() -> service.abrir(3L, 15_200, null, null, ASESOR)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo(CodigosError.ORDEN_ABIERTA);
		verify(ordenes, never()).save(any());
	}

	@Test
	void avanzaTerminandoLaEtapaActualYNuncaRegresa() {
		OrdenServicio orden = orden(5L);
		EtapaOrden recibido = EtapaOrden.empezar(5L, EtapaTaller.RECIBIDO, AHORA.minusSeconds(3600), null);
		List<EtapaOrden> recorridas = new ArrayList<>(List.of(recibido));
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden));
		when(etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(5L)).thenAnswer(invocacion -> List.copyOf(recorridas));
		when(etapas.save(any(EtapaOrden.class))).thenAnswer(invocacion -> {
			recorridas.add(invocacion.getArgument(0));
			return invocacion.getArgument(0);
		});
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));
		when(adicionales.findByOrdenServicioIdOrderByIdAsc(5L)).thenReturn(List.of());
		sinDetalle(5L);

		OrdenEnVivo enReparacion = service.avanzar(5L, EtapaTaller.EN_REPARACION, "Cambio de balatas", null);

		assertThat(recibido.getFin()).isEqualTo(AHORA);
		assertThat(enReparacion.etapa()).isEqualTo(EtapaTaller.EN_REPARACION);
		assertThat(enReparacion.estatus()).isEqualTo(EstatusOrden.EN_PROCESO);
		assertThat(enReparacion.avance()).isEqualTo(43);
		verify(eventos).publishEvent(new OrdenServicioCambiada(5L, 3L, 9L, EstatusOrden.EN_PROCESO,
				EtapaTaller.EN_REPARACION, MotivoCambio.ETAPA));
		assertThatThrownBy(() -> service.avanzar(5L, EtapaTaller.DIAGNOSTICO, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void alQuedarListaLosAdicionalesAprobadosPasanAlTicketSinContarseDosVeces() {
		OrdenServicio orden = orden(5L);
		orden.agregarRenglon(new RenglonNuevo(TipoRenglon.MANO_DE_OBRA, "Afinación", 1, new BigDecimal("900.00")));
		TrabajoAdicional balatas = adicional(11L, 5L, "1650.50");
		balatas.responder(true, 9L, "firmas/a", AHORA);
		TrabajoAdicional lavado = adicional(12L, 5L, "200.00");
		lavado.responder(false, 9L, "firmas/b", AHORA);
		EtapaOrden lavando = EtapaOrden.empezar(5L, EtapaTaller.LAVADO, AHORA.minusSeconds(600), null);
		List<EtapaOrden> recorridas = new ArrayList<>(List.of(lavando));
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden));
		when(etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(5L)).thenAnswer(invocacion -> List.copyOf(recorridas));
		when(etapas.save(any(EtapaOrden.class))).thenAnswer(invocacion -> {
			recorridas.add(invocacion.getArgument(0));
			return invocacion.getArgument(0);
		});
		when(vehiculos.buscarPorId(3L)).thenReturn(Optional.of(SENTRA));
		when(adicionales.findByOrdenServicioIdOrderByIdAsc(5L)).thenReturn(List.of(balatas, lavado));
		sinDetalle(5L);

		OrdenEnVivo lista = service.avanzar(5L, EtapaTaller.LISTO_PARA_ENTREGA, null, null);

		assertThat(balatas.estaIncorporado()).isTrue();
		assertThat(lavado.estaIncorporado()).isFalse();
		assertThat(orden.getRenglonesManoDeObra()).hasSize(1);
		assertThat(orden.getTotal()).isEqualByComparingTo("2550.50");
		assertThat(lista.estatus()).isEqualTo(EstatusOrden.TERMINADO);
		assertThat(lista.totalEstimado()).isEqualByComparingTo("2550.50");
		verify(eventos).publishEvent(new OrdenServicioCambiada(5L, 3L, 9L, EstatusOrden.TERMINADO,
				EtapaTaller.LISTO_PARA_ENTREGA, MotivoCambio.ETAPA));
	}

	@Test
	void unRenglonSoloSeAgregaAUnaOrdenAbierta() {
		OrdenServicio orden = orden(5L);
		orden.pasarA(EtapaTaller.LISTO_PARA_ENTREGA, AHORA);
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden));

		assertThatThrownBy(() -> service.agregarRenglon(5L,
				new RenglonNuevo(TipoRenglon.REFACCION, "Bujías", 4, new BigDecimal("95.50"))))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.ORDEN_CERRADA);
		assertThat(orden.getTotal()).isEqualByComparingTo("0.00");
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void unaOrdenSinLineaDeTiempoNoSeAvanza() {
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden(5L)));
		when(etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(5L)).thenReturn(List.of());

		assertThatThrownBy(() -> service.avanzar(5L, EtapaTaller.LAVADO, null, null))
				.isInstanceOf(ConflictoException.class).extracting("codigo").isEqualTo(CodigosError.ORDEN_CERRADA);
	}

	@Test
	void aprobarGuardaLaFirmaYSumaElAdicionalAlTotalEstimado() {
		OrdenServicio orden = orden(5L);
		TrabajoAdicional balatas = adicional(11L, 5L, "1650.50");
		TrabajoAdicional lavado = adicional(12L, 5L, "200.00");
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden));
		when(adicionales.findByIdAndOrdenServicioId(11L, 5L)).thenReturn(Optional.of(balatas));
		when(almacen.guardar("firmas", "image/png", FIRMA))
				.thenReturn(new ArchivoGuardado("firmas/0f8c2b6e-2f6d-4c1e-9c2a-1b2c3d4e5f60", "image/png", 11));
		when(etapas.findByOrdenServicioIdOrderByInicioAscIdAsc(5L))
				.thenReturn(List.of(EtapaOrden.empezar(5L, EtapaTaller.ESPERANDO_APROBACION, AHORA, null)));
		when(adicionales.findByOrdenServicioIdOrderByIdAsc(5L)).thenReturn(List.of(balatas, lavado));
		sinDetalle(5L);

		OrdenEnVivo respondida = service.responder(5L, 11L, true, FIRMA, 9L);

		assertThat(balatas.getEstado()).isEqualTo(EstadoAdicional.APROBADO);
		assertThat(balatas.getRespondidoPor()).isEqualTo(9L);
		assertThat(balatas.getFirmaUrl()).isEqualTo("firmas/0f8c2b6e-2f6d-4c1e-9c2a-1b2c3d4e5f60");
		assertThat(balatas.getRespondidoEn()).isEqualTo(AHORA);
		assertThat(respondida.totalAdicionalesAprobados()).isEqualByComparingTo("1650.50");
		assertThat(respondida.totalAdicionalesPendientes()).isEqualByComparingTo("200.00");
		assertThat(respondida.totalEstimado()).isEqualByComparingTo("1650.50");
	}

	@Test
	void unAdicionalYaRespondidoOUnaOrdenTerminadaNoSeResponden() {
		OrdenServicio orden = orden(5L);
		TrabajoAdicional respondido = adicional(11L, 5L, "100.00");
		respondido.responder(false, 9L, "firmas/previa", AHORA);
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden));
		when(adicionales.findByIdAndOrdenServicioId(11L, 5L)).thenReturn(Optional.of(respondido));
		when(adicionales.findByIdAndOrdenServicioId(12L, 5L)).thenReturn(Optional.of(adicional(12L, 5L, "50.00")));

		assertThatThrownBy(() -> service.responder(5L, 11L, true, FIRMA, 9L)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo(CodigosError.ADICIONAL_YA_RESPONDIDO);
		orden.pasarA(EtapaTaller.LISTO_PARA_ENTREGA, AHORA);
		assertThatThrownBy(() -> service.responder(5L, 12L, true, FIRMA, 9L)).isInstanceOf(ConflictoException.class)
				.extracting("codigo").isEqualTo(CodigosError.ORDEN_CERRADA);
		verify(almacen, never()).guardar(anyString(), anyString(), any());
	}

	@Test
	void unaFirmaQueNoEsPngSeRechazaSinGuardarNada() {
		when(ordenes.bloquear(5L)).thenReturn(Optional.of(orden(5L)));
		when(adicionales.findByIdAndOrdenServicioId(11L, 5L)).thenReturn(Optional.of(adicional(11L, 5L, "100.00")));

		assertThatThrownBy(() -> service.responder(5L, 11L, true, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 9L))
				.isInstanceOf(IllegalArgumentException.class);
		verify(almacen, never()).guardar(anyString(), anyString(), any());
	}

	@Test
	void sinVehiculosNoHayOrdenesEnCurso() {
		assertThat(service.enCurso(List.of())).isEmpty();
		verify(ordenes, never()).enCurso(anyCollection(), eq(EstatusOrden.ENTREGADO));
	}
}
