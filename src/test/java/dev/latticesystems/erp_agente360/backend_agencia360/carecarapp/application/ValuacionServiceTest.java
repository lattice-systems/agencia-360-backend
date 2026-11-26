package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TomaValuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Valuacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ValuacionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ValuacionSolicitada;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos.EstadoProspecto;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ValuacionServiceTest {

	private static final long CLIENTE = 1L;
	private static final long ESTIMACION = 2L;
	private static final long VEHICULO = 3L;
	private static final Instant AHORA = Instant.parse("2026-11-25T18:00:00Z");
	private static final LocalDate HOY = LocalDate.of(2026, 11, 25);

	@Mock
	private ValuacionRepository valuaciones;
	@Mock
	private EstimacionTomaCuentaRepository estimaciones;
	@Mock
	private CitaRepository citas;
	@Mock
	private VehiculosDeClientes vehiculos;
	@Mock
	private SolicitudesEstreno solicitudesEstreno;
	@Mock
	private ConsultaProspectos prospectos;
	@Mock
	private ConsultaVendedores vendedores;
	@Mock
	private DirectorioClientes clientes;
	@Mock
	private TomaCuentaService tomaCuenta;
	@Mock
	private AgendaAppService agenda;
	@Mock
	private AlmacenArchivos archivos;
	@Mock
	private ApplicationEventPublisher eventos;

	private ValuacionService servicio;

	@BeforeEach
	void preparar() {
		servicio = new ValuacionService(valuaciones, estimaciones, citas, vehiculos, solicitudesEstreno, prospectos,
				vendedores, clientes, tomaCuenta, agenda, archivos, eventos, new ValuacionService.Plazos(7, 7, 30),
				Clock.fixed(AHORA, AgendaAppService.ZONA_AGENCIA));
		EstimacionTomaCuenta estimacion = mock(EstimacionTomaCuenta.class);
		lenient().when(estimacion.getClienteId()).thenReturn(CLIENTE);
		lenient().when(estimacion.getVehiculoClienteId()).thenReturn(VEHICULO);
		lenient().when(estimaciones.findById(ESTIMACION)).thenReturn(Optional.of(estimacion));
		VehiculoDeCliente vehiculo = mock(VehiculoDeCliente.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
		lenient().when(vehiculo.propietario().id()).thenReturn(CLIENTE);
		lenient().when(vehiculos.buscarPorId(VEHICULO)).thenReturn(Optional.of(vehiculo));
		lenient().when(valuaciones.save(any())).thenAnswer(llamada -> {
			Valuacion nueva = llamada.getArgument(0);
			ReflectionTestUtils.setField(nueva, "id", 10L);
			return nueva;
		});
		lenient().when(clientes.buscarPorId(CLIENTE))
				.thenReturn(Optional.of(new ClienteRegistrado(CLIENTE, "Ana", "ana@correo.com")));
		lenient().when(solicitudesEstreno.delCliente(CLIENTE, 0, 50)).thenReturn(List.of());
	}

	@Test
	void reutilizaElProspectoAbiertoDeEstrenoYNoPublicaEvento() {
		when(solicitudesEstreno.delCliente(CLIENTE, 0, 50))
				.thenReturn(List.of(estreno(null, 71L), estreno(ESTIMACION, 70L)));
		abierto(70L);

		servicio.solicitar(CLIENTE, ESTIMACION);

		// Primero el de la solicitud con la misma estimación.
		assertThat(guardada().getProspectoId()).isEqualTo(70L);
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void sinEstrenoReutilizaElDeSuValuacionMasReciente() {
		when(solicitudesEstreno.delCliente(CLIENTE, 0, 50)).thenReturn(List.of(estreno(null, 71L)));
		terminado(71L);
		when(valuaciones.prospectosDelCliente(CLIENTE)).thenReturn(List.of(80L));
		abierto(80L);

		servicio.solicitar(CLIENTE, ESTIMACION);

		assertThat(guardada().getProspectoId()).isEqualTo(80L);
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void sinProspectoAbiertoPublicaValuacionSolicitada() {
		when(solicitudesEstreno.delCliente(CLIENTE, 0, 50)).thenReturn(List.of(estreno(ESTIMACION, 70L)));
		terminado(70L);

		ValuacionService.Solicitud resultado = servicio.solicitar(CLIENTE, ESTIMACION);

		assertThat(resultado.creada()).isTrue();
		assertThat(guardada().getProspectoId()).isNull();
		verify(eventos).publishEvent(new ValuacionSolicitada(10L, "Ana", "ana@correo.com", null));
	}

	@Test
	void devuelveLaAbiertaDelMismoVehiculoSinCrearOtra() {
		Valuacion abierta = existente();
		when(valuaciones.findFirstByClienteIdAndVehiculoClienteIdAndEstadoIn(CLIENTE, VEHICULO,
				EstadoValuacion.abiertos())).thenReturn(Optional.of(abierta));

		ValuacionService.Solicitud resultado = servicio.solicitar(CLIENTE, ESTIMACION);

		assertThat(resultado.creada()).isFalse();
		assertThat(resultado.valuacion().id()).isEqualTo(10L);
		verify(valuaciones, never()).save(any());
		verify(eventos, never()).publishEvent(any(Object.class));
	}

	@Test
	void unaCitaCanceladaLaReabreAlConsultarla() {
		Valuacion valuacion = existente();
		valuacion.inspeccionAgendada(50L);
		when(valuaciones.bloquearDeCliente(10L, CLIENTE)).thenReturn(Optional.of(valuacion));
		cita(50L, EstadoCita.CANCELADA, HOY.plusDays(1));

		ValuacionApp vista = servicio.ver(CLIENTE, 10L);

		assertThat(vista.estado()).isEqualTo(EstadoValuacion.SOLICITADA);
		assertThat(vista.cita()).isNull();
		assertThat(vista.puedeAgendar()).isTrue();
	}

	@Test
	void elAsesorEsElVendedorDelProspectoLigado() {
		Valuacion valuacion = existente();
		valuacion.ligarProspecto(70L);
		when(valuaciones.bloquearDeCliente(10L, CLIENTE)).thenReturn(Optional.of(valuacion));
		abierto(70L);
		when(vendedores.nombreDe(5L)).thenReturn(Optional.of("Vera"));

		assertThat(servicio.ver(CLIENTE, 10L).asesor()).isEqualTo("Vera");
	}

	@Test
	void laFotoAnteriorSeBorraSoloDespuesDelCommit() throws Exception {
		Valuacion valuacion = conUnaFoto();
		TransactionSynchronizationManager.initSynchronization();
		try {
			servicio.subirFoto(CLIENTE, 10L, TomaValuacion.FRENTE, png());
			verify(archivos, never()).borrar(any());

			terminar(TransactionSynchronization.STATUS_COMMITTED);

			verify(archivos).borrar("valuacion-fotos/anterior");
			verify(archivos, never()).borrar("valuacion-fotos/nueva");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
		assertThat(valuacion.getFotos().get(TomaValuacion.FRENTE).clave()).isEqualTo("valuacion-fotos/nueva");
	}

	@Test
	void siLaTransaccionFallaSeBorraLaFotoNuevaYNoLaAnterior() throws Exception {
		conUnaFoto();
		TransactionSynchronizationManager.initSynchronization();
		try {
			servicio.subirFoto(CLIENTE, 10L, TomaValuacion.FRENTE, png());

			terminar(TransactionSynchronization.STATUS_ROLLED_BACK);

			verify(archivos).borrar("valuacion-fotos/nueva");
			verify(archivos, never()).borrar("valuacion-fotos/anterior");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private Valuacion conUnaFoto() {
		Valuacion valuacion = existente();
		valuacion.ponerFoto(TomaValuacion.FRENTE, "valuacion-fotos/anterior", AHORA);
		when(valuaciones.bloquearDeCliente(10L, CLIENTE)).thenReturn(Optional.of(valuacion));
		when(archivos.guardar(eq("valuacion-fotos"), eq("image/jpeg"), any()))
				.thenReturn(new AlmacenArchivos.ArchivoGuardado("valuacion-fotos/nueva", "image/jpeg", 1));
		return valuacion;
	}

	/** Lo que hace Spring al cerrar la transacción. */
	private static void terminar(int estado) {
		for (TransactionSynchronization pendiente : TransactionSynchronizationManager.getSynchronizations()) {
			if (estado == TransactionSynchronization.STATUS_COMMITTED) {
				pendiente.afterCommit();
			}
			pendiente.afterCompletion(estado);
		}
	}

	private static byte[] png() throws Exception {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", salida);
		return salida.toByteArray();
	}

	private Cita cita(long id, EstadoCita estado, LocalDate fecha) {
		Cita cita = mock(Cita.class);
		lenient().when(cita.getId()).thenReturn(id);
		lenient().when(cita.getEstado()).thenReturn(estado);
		lenient().when(cita.getFecha()).thenReturn(fecha);
		lenient().when(citas.findById(id)).thenReturn(Optional.of(cita));
		return cita;
	}

	private Valuacion existente() {
		Valuacion valuacion = Valuacion.solicitar(CLIENTE, ESTIMACION, VEHICULO);
		ReflectionTestUtils.setField(valuacion, "id", 10L);
		return valuacion;
	}

	private Valuacion guardada() {
		ArgumentCaptor<Valuacion> captor = ArgumentCaptor.forClass(Valuacion.class);
		verify(valuaciones).save(captor.capture());
		return captor.getValue();
	}

	private void abierto(long prospecto) {
		lenient().when(prospectos.estado(prospecto))
				.thenReturn(Optional.of(new EstadoProspecto("VISITA", false, 5L)));
	}

	private void terminado(long prospecto) {
		lenient().when(prospectos.estado(prospecto)).thenReturn(Optional.of(new EstadoProspecto("CERRADO", true, 5L)));
	}

	private static SolicitudesEstreno.Solicitud estreno(Long estimacion, Long prospecto) {
		return new SolicitudesEstreno.Solicitud(1L, 1L, "Kicks", "COTIZACION", null, null, null, null, null, null,
				estimacion, prospecto, AHORA);
	}

}
