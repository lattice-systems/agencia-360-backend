package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ArchivosArco;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ArcoService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ExportadorDeDatos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudArcoCreada;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudesArco;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/** Reglas de V2-M22.02 sin base de datos: flujo, vigencia, dueño y limpieza. */
class ArcoServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
	private static final long CLIENTE = 7;

	private SolicitudesEnMemoria solicitudes;
	private ArchivosEnMemoria almacen;
	private ExportadorFalso exportador;
	private EventosRecolectados eventos;
	private ArcoService servicio;

	@BeforeEach
	void servicio() {
		solicitudes = new SolicitudesEnMemoria();
		almacen = new ArchivosEnMemoria();
		exportador = new ExportadorFalso();
		eventos = new EventosRecolectados();
		servicio = en(AHORA);
	}

	private ArcoService en(Instant instante) {
		return new ArcoService(solicitudes, exportador, almacen, eventos, Clock.fixed(instante, ZoneOffset.UTC));
	}

	@Test
	void pedirAccesoCreaUnaSolicitudPendienteYAvisaQueHayQueProcesarla() {
		var resultado = servicio.pedirAcceso(CLIENTE);

		assertThat(resultado.nueva()).isTrue();
		assertThat(resultado.solicitud().tipo()).isEqualTo("ACCESO");
		assertThat(resultado.solicitud().estado()).isEqualTo("PENDIENTE");
		assertThat(resultado.solicitud().creadaEn()).isEqualTo(AHORA);
		assertThat(resultado.solicitud().resueltaEn()).isNull();
		assertThat(resultado.solicitud().venceEn()).isNull();
		assertThat(eventos.de(SolicitudArcoCreada.class)).singleElement()
				.satisfies(e -> assertThat(e.solicitudId()).isEqualTo(resultado.solicitud().id()));
	}

	@Test
	void conUnaPendienteDevuelveEsaSinAbrirOtra() {
		var primera = servicio.pedirAcceso(CLIENTE);
		eventos.limpiar();

		var segunda = servicio.pedirAcceso(CLIENTE);

		assertThat(segunda.nueva()).isFalse();
		assertThat(segunda.solicitud().id()).isEqualTo(primera.solicitud().id());
		assertThat(solicitudes.todas).hasSize(1);
		assertThat(eventos.de(SolicitudArcoCreada.class)).isEmpty();
	}

	@Test
	void sidosPeticionesSeCruzanGanaLaPrimera() {
		var ganadora = solicitudes.crear(CLIENTE, "ACCESO", AHORA).orElseThrow();
		solicitudes.ocultarLaPendienteUnaVez = true;

		var resultado = servicio.pedirAcceso(CLIENTE);

		assertThat(resultado.nueva()).isFalse();
		assertThat(resultado.solicitud().id()).isEqualTo(ganadora.id());
	}

	@Test
	void unaSolicitudNuevaBorraElArchivoDeLaAnterior() {
		var anterior = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(anterior.id());
		assertThat(almacen.archivos).hasSize(1);

		var nueva = servicio.pedirAcceso(CLIENTE);

		assertThat(nueva.nueva()).isTrue();
		assertThat(almacen.archivos).isEmpty();
		assertThat(solicitudes.buscar(anterior.id()).orElseThrow().archivoUrl()).isNull();
	}

	@Test
	void lasSolicitudesDeOtroClienteNoSeTocan() {
		var ajena = solicitudes.crear(99, "ACCESO", AHORA).orElseThrow();
		servicio.procesar(ajena.id());
		long archivosAntes = almacen.archivos.size();

		servicio.pedirAcceso(CLIENTE);

		assertThat(almacen.archivos).hasSize((int) archivosAntes);
		assertThat(solicitudes.buscar(ajena.id()).orElseThrow().archivoUrl()).isNotNull();
	}

	@Test
	void procesarGuardaElArchivoResuelveYAvisaAlCliente() {
		exportador.contenido = "{\"cliente\":{}}".getBytes(StandardCharsets.UTF_8);
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();

		servicio.procesar(pedida.id());

		var guardada = solicitudes.buscar(pedida.id()).orElseThrow();
		assertThat(guardada.estado()).isEqualTo("RESUELTA");
		assertThat(guardada.resueltaEn()).isEqualTo(AHORA);
		assertThat(guardada.archivoUrl()).isEqualTo("/api/v1/app/privacidad/arco/" + pedida.id() + "/archivo");
		assertThat(almacen.archivos.get(pedida.id())).isEqualTo(exportador.contenido);
		assertThat(exportador.pedidosDe).containsExactly(CLIENTE);
		assertThat(eventos.de(AvisoAlCliente.class)).singleElement().satisfies(aviso -> {
			assertThat(aviso.clienteId()).isEqualTo(CLIENTE);
			assertThat(aviso.evento()).isEqualTo("arco.estado");
			assertThat(aviso.datos()).containsEntry("solicitudId", pedida.id()).containsEntry("tipo", "ACCESO")
					.containsEntry("estado", "RESUELTA");
		});
	}

	@Test
	void siElExportadorFallaLaSolicitudQuedaFallidaYSeAvisa() {
		exportador.falla = new IllegalStateException("la base se cayó para ana@correo.mx");
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();

		servicio.procesar(pedida.id());

		var guardada = solicitudes.buscar(pedida.id()).orElseThrow();
		assertThat(guardada.estado()).isEqualTo("FALLIDA");
		assertThat(guardada.archivoUrl()).isNull();
		assertThat(almacen.archivos).isEmpty();
		assertThat(eventos.de(AvisoAlCliente.class)).singleElement()
				.satisfies(aviso -> assertThat(aviso.datos()).containsEntry("estado", "FALLIDA"));
	}

	@Test
	void siFallaAlResolverNoQuedaArchivoHuerfano() {
		solicitudes.fallaAlResolver = true;
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();

		servicio.procesar(pedida.id());

		assertThat(solicitudes.buscar(pedida.id()).orElseThrow().estado()).isEqualTo("FALLIDA");
		assertThat(almacen.archivos).isEmpty();
	}

	@Test
	void unaFallidaPermiteIntentarDeNuevo() {
		exportador.falla = new IllegalStateException("x");
		var primera = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(primera.id());
		exportador.falla = null;

		var otra = servicio.pedirAcceso(CLIENTE);

		assertThat(otra.nueva()).isTrue();
		assertThat(otra.solicitud().id()).isNotEqualTo(primera.id());
	}

	@Test
	void procesarDosVecesNoRepiteNiDuplicaElArchivo() {
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(pedida.id());
		eventos.limpiar();

		servicio.procesar(pedida.id());

		assertThat(almacen.archivos).hasSize(1);
		assertThat(exportador.pedidosDe).hasSize(1);
		assertThat(eventos.de(AvisoAlCliente.class)).isEmpty();
	}

	@Test
	void procesarUnaSolicitudQueNoExisteNoHaceNada() {
		servicio.procesar(12345);
		assertThat(almacen.archivos).isEmpty();
		assertThat(eventos.todos).isEmpty();
	}

	@Test
	void unaResueltaTrae_Archivo_HastaSieteDiasDespues() {
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(pedida.id());

		var enLista = servicio.misSolicitudes(CLIENTE).getFirst();
		assertThat(enLista.estado()).isEqualTo("RESUELTA");
		assertThat(enLista.resueltaEn()).isEqualTo(AHORA);
		assertThat(enLista.venceEn()).isEqualTo(AHORA.plus(Duration.ofDays(7)));

		var unSegundoAntes = en(AHORA.plus(Duration.ofDays(7)).minusSeconds(1));
		assertThat(unSegundoAntes.misSolicitudes(CLIENTE).getFirst().estado()).isEqualTo("RESUELTA");
		var alVencer = en(AHORA.plus(Duration.ofDays(7)));
		assertThat(alVencer.misSolicitudes(CLIENTE).getFirst().estado()).isEqualTo("VENCIDA");
	}

	@Test
	void misSolicitudesVienenLasMasRecientesPrimeroYSoloLasMias() {
		var a = solicitudes.crear(CLIENTE, "ACCESO", AHORA.minusSeconds(30)).orElseThrow();
		solicitudes.fallar(a.id(), AHORA.minusSeconds(29));
		var b = solicitudes.crear(CLIENTE, "ACCESO", AHORA.minusSeconds(10)).orElseThrow();
		solicitudes.crear(99, "ACCESO", AHORA).orElseThrow();

		var lista = servicio.misSolicitudes(CLIENTE);

		assertThat(lista).extracting(ArcoService.SolicitudArcoApp::id).containsExactly(b.id(), a.id());
	}

	@Test
	void descargarEntregaElArchivoSoloALaCuentaDuena() {
		exportador.contenido = "{\"hola\":1}".getBytes(StandardCharsets.UTF_8);
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(pedida.id());

		var archivo = servicio.archivo(CLIENTE, pedida.id());

		assertThat(archivo.contenido()).isEqualTo(exportador.contenido);
		assertThat(archivo.nombre()).isEqualTo("mis-datos-carecar-2026-11-25.json");
		assertThatThrownBy(() -> servicio.archivo(99, pedida.id())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.archivo(CLIENTE, 12345)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void noSeDescargaLoQueNoEstaListoNiLoVencido() {
		var pendiente = servicio.pedirAcceso(CLIENTE).solicitud();
		assertThatThrownBy(() -> servicio.archivo(CLIENTE, pendiente.id()))
				.isInstanceOf(RecursoNoEncontradoException.class);

		servicio.procesar(pendiente.id());
		var vencido = en(AHORA.plus(Duration.ofDays(7)));
		assertThatThrownBy(() -> vencido.archivo(CLIENTE, pendiente.id()))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unaFallidaNoTieneArchivo() {
		exportador.falla = new IllegalStateException("x");
		var pedida = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(pedida.id());
		assertThatThrownBy(() -> servicio.archivo(CLIENTE, pedida.id()))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void laLimpiezaBorraLosArchivosVencidosYDejaLosVigentes() {
		var vieja = servicio.pedirAcceso(CLIENTE).solicitud();
		servicio.procesar(vieja.id());
		var otraCuenta = en(AHORA.plus(Duration.ofDays(6))).pedirAcceso(99).solicitud();
		en(AHORA.plus(Duration.ofDays(6))).procesar(otraCuenta.id());
		assertThat(almacen.archivos).hasSize(2);

		en(AHORA.plus(Duration.ofDays(8))).limpiarVencidos();

		assertThat(almacen.archivos).hasSize(1);
		assertThat(solicitudes.buscar(vieja.id()).orElseThrow().archivoUrl()).isNull();
		assertThat(solicitudes.buscar(otraCuenta.id()).orElseThrow().archivoUrl()).isNotNull();
	}

	/** Doble en memoria de la tabla; la integración prueba la real. */
	private static final class SolicitudesEnMemoria implements SolicitudesArco {

		final List<Solicitud> todas = new ArrayList<>();
		boolean ocultarLaPendienteUnaVez;
		boolean fallaAlResolver;

		@Override
		public Optional<Solicitud> crear(long clienteId, String tipo, Instant ahora) {
			if (todas.stream().anyMatch(
					s -> s.clienteId() == clienteId && s.tipo().equals(tipo) && s.estado().equals("PENDIENTE"))) {
				return Optional.empty();
			}
			Solicitud s = new Solicitud(todas.size() + 1L, clienteId, tipo, "PENDIENTE", null, ahora, null);
			todas.add(s);
			return Optional.of(s);
		}

		@Override
		public Optional<Solicitud> buscar(long id) {
			return todas.stream().filter(s -> s.id() == id).findFirst();
		}

		@Override
		public Optional<Solicitud> pendienteDe(long clienteId, String tipo) {
			if (ocultarLaPendienteUnaVez) {
				ocultarLaPendienteUnaVez = false;
				return Optional.empty();
			}
			return todas.stream()
					.filter(s -> s.clienteId() == clienteId && s.tipo().equals(tipo) && s.estado().equals("PENDIENTE"))
					.findFirst();
		}

		@Override
		public List<Solicitud> delCliente(long clienteId, int limite) {
			return todas.stream().filter(s -> s.clienteId() == clienteId).sorted(Comparator
					.comparing(Solicitud::creadaEn).reversed().thenComparing(Solicitud::id, Comparator.reverseOrder()))
					.limit(limite).toList();
		}

		@Override
		public List<Solicitud> conArchivo(long clienteId, String tipo) {
			return todas.stream()
					.filter(s -> s.clienteId() == clienteId && s.tipo().equals(tipo) && s.archivoUrl() != null)
					.toList();
		}

		@Override
		public List<Solicitud> conArchivoResueltasAntesDe(Instant limite) {
			return todas.stream()
					.filter(s -> s.archivoUrl() != null && s.resueltaEn() != null && !s.resueltaEn().isAfter(limite))
					.toList();
		}

		@Override
		public void resolver(long id, String archivoUrl, Instant ahora) {
			if (fallaAlResolver) {
				throw new IllegalStateException("falla al resolver");
			}
			reemplazar(id,
					s -> new Solicitud(s.id(), s.clienteId(), s.tipo(), "RESUELTA", archivoUrl, s.creadaEn(), ahora));
		}

		@Override
		public void fallar(long id, Instant ahora) {
			reemplazar(id, s -> new Solicitud(s.id(), s.clienteId(), s.tipo(), "FALLIDA", null, s.creadaEn(), ahora));
		}

		@Override
		public void quitarArchivo(long id) {
			reemplazar(id, s -> new Solicitud(s.id(), s.clienteId(), s.tipo(), s.estado(), null, s.creadaEn(),
					s.resueltaEn()));
		}

		private void reemplazar(long id, java.util.function.UnaryOperator<Solicitud> cambio) {
			for (int i = 0; i < todas.size(); i++) {
				if (todas.get(i).id() == id) {
					todas.set(i, cambio.apply(todas.get(i)));
				}
			}
		}
	}

	private static final class ArchivosEnMemoria implements ArchivosArco {

		final Map<Long, byte[]> archivos = new HashMap<>();

		@Override
		public void guardar(long solicitudId, byte[] contenido) {
			archivos.put(solicitudId, contenido);
		}

		@Override
		public Optional<byte[]> leer(long solicitudId) {
			return Optional.ofNullable(archivos.get(solicitudId));
		}

		@Override
		public void borrar(long solicitudId) {
			archivos.remove(solicitudId);
		}
	}

	private static final class ExportadorFalso implements ExportadorDeDatos {

		byte[] contenido = "{}".getBytes(StandardCharsets.UTF_8);
		RuntimeException falla;
		final List<Long> pedidosDe = new ArrayList<>();

		@Override
		public byte[] exportar(long clienteId, Instant ahora) {
			pedidosDe.add(clienteId);
			if (falla != null) {
				throw falla;
			}
			return contenido;
		}
	}

	private static final class EventosRecolectados implements ApplicationEventPublisher {

		final List<Object> todos = new ArrayList<>();

		@Override
		public void publishEvent(ApplicationEvent evento) {
			todos.add(evento);
		}

		@Override
		public void publishEvent(Object evento) {
			todos.add(evento);
		}

		<T> List<T> de(Class<T> tipo) {
			return todos.stream().filter(tipo::isInstance).map(tipo::cast).toList();
		}

		void limpiar() {
			todos.clear();
		}
	}
}
