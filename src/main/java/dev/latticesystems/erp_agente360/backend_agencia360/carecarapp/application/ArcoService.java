package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudesArco.Solicitud;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derechos ARCO de la cuenta de la app (V2-M22.02): la copia de los datos de
 * una persona se prepara sin hacerla esperar, se entrega solo a su cuenta y
 * deja de estar disponible a los 7 días. Por cuenta nunca hay más de un
 * archivo.
 */
@Service
public class ArcoService {

	private static final Logger LOG = LoggerFactory.getLogger(ArcoService.class);

	static final String ACCESO = "ACCESO";
	static final String PENDIENTE = "PENDIENTE";
	static final String RESUELTA = "RESUELTA";
	static final String FALLIDA = "FALLIDA";
	static final String VENCIDA = "VENCIDA";

	/** Ruta de la API desde la que se descarga la copia de una solicitud. */
	static String rutaDelArchivo(long solicitudId) {
		return "/api/v1/app/privacidad/arco/" + solicitudId + "/archivo";
	}

	/** Evento SSE con el que la app se entera de que una solicitud cambió. */
	public static final String EVENTO = "arco.estado";

	/** Cuánto tiempo se puede descargar el archivo desde que se resuelve. */
	public static final Duration VIGENCIA = Duration.ofDays(7);

	private static final int MAXIMO_EN_LISTA = 20;

	/**
	 * Una solicitud como la ve la app. {@code VENCIDA} se calcula, no se guarda.
	 */
	public record SolicitudArcoApp(long id, String tipo, String estado, Instant creadaEn, Instant resueltaEn,
			Instant venceEn) {
	}

	/** Lo que responde pedir el acceso: la solicitud y si se abrió una nueva. */
	public record Resultado(SolicitudArcoApp solicitud, boolean nueva) {
	}

	/** El archivo para descargar y el nombre con el que se guarda. */
	public record ArchivoArco(byte[] contenido, String nombre) {
	}

	private final SolicitudesArco solicitudes;
	private final ExportadorDeDatos exportador;
	private final ArchivosArco archivos;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;

	@Autowired
	public ArcoService(SolicitudesArco solicitudes, ExportadorDeDatos exportador, ArchivosArco archivos,
			ApplicationEventPublisher eventos) {
		this(solicitudes, exportador, archivos, eventos, Clock.systemUTC());
	}

	public ArcoService(SolicitudesArco solicitudes, ExportadorDeDatos exportador, ArchivosArco archivos,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.solicitudes = solicitudes;
		this.exportador = exportador;
		this.archivos = archivos;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * Pide una copia de los datos de la cuenta. Si ya hay una solicitud de acceso
	 * pendiente, devuelve esa. Una solicitud nueva borra el archivo de la anterior.
	 */
	@Transactional
	public Resultado pedirAcceso(long clienteId) {
		Instant ahora = Instant.now(reloj);
		Optional<Solicitud> pendiente = solicitudes.pendienteDe(clienteId, ACCESO);
		if (pendiente.isPresent()) {
			return new Resultado(aApp(pendiente.get(), ahora), false);
		}
		borrarArchivosDeAntes(clienteId);
		Optional<Solicitud> nueva = solicitudes.crear(clienteId, ACCESO, ahora);
		if (nueva.isEmpty()) {
			// Otra petición abrió la pendiente entre la lectura y la inserción.
			return new Resultado(aApp(solicitudes.pendienteDe(clienteId, ACCESO).orElseThrow(), ahora), false);
		}
		eventos.publishEvent(new SolicitudArcoCreada(nueva.get().id(), clienteId, ACCESO));
		return new Resultado(aApp(nueva.get(), ahora), true);
	}

	/** Las últimas solicitudes de la cuenta, más recientes primero. */
	@Transactional(readOnly = true)
	public List<SolicitudArcoApp> misSolicitudes(long clienteId) {
		Instant ahora = Instant.now(reloj);
		return solicitudes.delCliente(clienteId, MAXIMO_EN_LISTA).stream().map(s -> aApp(s, ahora)).toList();
	}

	/**
	 * El archivo de una solicitud de acceso, solo para la cuenta dueña.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si no existe, es de otra cuenta, no está resuelta o ya venció
	 *             (404)
	 */
	@Transactional(readOnly = true)
	public ArchivoArco archivo(long clienteId, long solicitudId) {
		Instant ahora = Instant.now(reloj);
		Solicitud solicitud = solicitudes.buscar(solicitudId).filter(s -> s.clienteId() == clienteId)
				.orElseThrow(ArcoService::archivoNoDisponible);
		if (!ACCESO.equals(solicitud.tipo()) || !RESUELTA.equals(aApp(solicitud, ahora).estado())
				|| solicitud.archivoUrl() == null) {
			throw archivoNoDisponible();
		}
		byte[] contenido = archivos.leer(solicitud.id()).orElseThrow(ArcoService::archivoNoDisponible);
		String nombre = "mis-datos-carecar-" + solicitud.resueltaEn().atZone(ZoneOffset.UTC).toLocalDate() + ".json";
		return new ArchivoArco(contenido, nombre);
	}

	/**
	 * Prepara una solicitud abierta: arma el archivo, lo guarda y avisa a la
	 * cuenta. Si algo falla la deja {@code FALLIDA} y también avisa. Es seguro
	 * repetirlo: lo que ya no está pendiente no se vuelve a procesar.
	 */
	@Transactional
	public void procesar(long solicitudId) {
		Optional<Solicitud> encontrada = solicitudes.buscar(solicitudId);
		if (encontrada.isEmpty() || !PENDIENTE.equals(encontrada.get().estado())) {
			return;
		}
		Solicitud solicitud = encontrada.get();
		Instant ahora = Instant.now(reloj);
		try {
			archivos.guardar(solicitud.id(), exportador.exportar(solicitud.clienteId(), ahora));
			solicitudes.resolver(solicitud.id(), rutaDelArchivo(solicitud.id()), ahora);
			avisar(solicitud, RESUELTA);
		} catch (RuntimeException ex) {
			// Solo la clase del error: su mensaje puede traer datos de la persona.
			LOG.warn("No se pudo preparar la solicitud ARCO {}: {}", solicitud.id(), ex.getClass().getSimpleName());
			archivos.borrar(solicitud.id());
			solicitudes.fallar(solicitud.id(), ahora);
			avisar(solicitud, FALLIDA);
		}
	}

	/**
	 * Atiende la solicitud en cuanto se confirma su creación, fuera de la petición.
	 */
	@ApplicationModuleListener
	void alCrearse(SolicitudArcoCreada evento) {
		procesar(evento.solicitudId());
	}

	/**
	 * Borra los archivos que pasaron su vigencia; la solicitud queda como
	 * {@code VENCIDA}.
	 */
	@Transactional
	public void limpiarVencidos() {
		Instant limite = Instant.now(reloj).minus(VIGENCIA);
		for (Solicitud vencida : solicitudes.conArchivoResueltasAntesDe(limite)) {
			borrarArchivo(vencida);
		}
	}

	private void borrarArchivosDeAntes(long clienteId) {
		for (Solicitud anterior : solicitudes.conArchivo(clienteId, ACCESO)) {
			borrarArchivo(anterior);
		}
	}

	private void borrarArchivo(Solicitud solicitud) {
		archivos.borrar(solicitud.id());
		solicitudes.quitarArchivo(solicitud.id());
	}

	private void avisar(Solicitud solicitud, String estado) {
		eventos.publishEvent(new AvisoAlCliente(solicitud.clienteId(), EVENTO,
				Map.of("solicitudId", solicitud.id(), "tipo", solicitud.tipo(), "estado", estado)));
	}

	private static SolicitudArcoApp aApp(Solicitud solicitud, Instant ahora) {
		Instant vence = ACCESO.equals(solicitud.tipo()) && RESUELTA.equals(solicitud.estado())
				&& solicitud.resueltaEn() != null ? solicitud.resueltaEn().plus(VIGENCIA) : null;
		String estado = vence != null && !ahora.isBefore(vence) ? VENCIDA : solicitud.estado();
		return new SolicitudArcoApp(solicitud.id(), solicitud.tipo(), estado, solicitud.creadaEn(),
				solicitud.resueltaEn(), vence);
	}

	private static RecursoNoEncontradoException archivoNoDisponible() {
		return new RecursoNoEncontradoException("El archivo no existe o ya no está disponible");
	}
}
