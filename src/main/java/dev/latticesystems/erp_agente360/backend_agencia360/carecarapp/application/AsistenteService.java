package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RespuestaAsistenteApp.FuenteAsistenteApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Manual;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ManualRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.DemasiadosIntentosException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ServicioNoDisponibleException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Asistente CareCar (V2-M24.03, ADR 0007): contesta con base en el manual del
 * modelo y año del vehículo del cliente, citando páginas. Si el manual no lo
 * dice, lo dice sin inventar. Al proveedor de IA solo le llegan la pregunta sin
 * datos personales, el modelo y año, y páginas del manual.
 */
@Service
public class AsistenteService {

	static final String INSTRUCCIONES = """
			Eres el asistente de CareCar, la app de servicio de una agencia Nissan en México.
			Respondes preguntas sobre el auto del cliente usando SOLO los fragmentos de su manual de propietario
			que vienen en el mensaje, cada uno marcado con su número de página.
			Reglas:
			- Español de México, claro y amable, en máximo 120 palabras.
			- No inventes nada que no esté en los fragmentos. Si no alcanzan para responder, pon encontrado=false
			  y di que no lo encontraste en su manual y que puede consultar a su asesor de la agencia.
			- En "paginas" pon solo los números de página de los fragmentos que usaste.
			- Si hay un riesgo de seguridad (testigo rojo, frenos, sobrecalentamiento, humo), recomienda detenerse
			  en un lugar seguro y llamar a la agencia.
			- No pidas ni repitas datos personales.
			""";
	static final String INSTRUCCIONES_TESTIGO = """
			Eres el asistente de CareCar, la app de servicio de una agencia Nissan en México.
			Te llega la foto de un tablero de auto. Identifica el testigo (luz) encendido más importante.
			Reglas:
			- Si la foto no muestra con claridad un testigo, pon reconocido=false y nada más.
			- testigo: su nombre común en español de México (por ejemplo "Presión de aceite", "Check engine").
			- gravedad: "roja" si hay que detenerse (aceite, temperatura, frenos, carga), "amarilla" si conviene
			  revisarlo pronto (check engine, presión de llantas, ABS) o "informativa" (luces, crucero).
			- queHacer: qué hacer ahora, en máximo 60 palabras, claro y amable. Si es roja, empieza por
			  detenerse en un lugar seguro.
			- No pidas ni repitas datos personales.
			""";
	private static final int PAGINAS_DE_CONTEXTO = 5;
	private static final int LARGO_MAXIMO_FRAGMENTO = 1800;
	private static final String PROVEEDOR_NINGUNO = "ninguno";

	private final VehiculosDeClientes vehiculos;
	private final ManualRepository manualRepository;
	private final FragmentosManual fragmentos;
	private final ConsultasAsistente consultas;
	private final IndexadorManuales indexador;
	private final ProveedorIA proveedor;
	private final Clock reloj;
	private final ZoneId zona;
	private final int limitePorCliente;
	private final int limiteGlobal;

	@Autowired
	public AsistenteService(VehiculosDeClientes vehiculos, ManualRepository manualRepository,
			FragmentosManual fragmentos, ConsultasAsistente consultas, IndexadorManuales indexador,
			ProveedorIA proveedor, @Value("${app.asistente.limite-por-cliente:10}") int limitePorCliente,
			@Value("${app.asistente.limite-global:450}") int limiteGlobal) {
		this(vehiculos, manualRepository, fragmentos, consultas, indexador, proveedor, Clock.systemUTC(),
				ZoneId.of("America/Mexico_City"), limitePorCliente, limiteGlobal);
	}

	AsistenteService(VehiculosDeClientes vehiculos, ManualRepository manualRepository, FragmentosManual fragmentos,
			ConsultasAsistente consultas, IndexadorManuales indexador, ProveedorIA proveedor, Clock reloj, ZoneId zona,
			int limitePorCliente, int limiteGlobal) {
		this.vehiculos = vehiculos;
		this.manualRepository = manualRepository;
		this.fragmentos = fragmentos;
		this.consultas = consultas;
		this.indexador = indexador;
		this.proveedor = proveedor;
		this.reloj = reloj;
		this.zona = zona;
		this.limitePorCliente = limitePorCliente;
		this.limiteGlobal = limiteGlobal;
	}

	/**
	 * Guarda si la respuesta fue útil (V2-M24.07); calificar otra vez la reemplaza.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la consulta no existe o no es del cliente (404)
	 */
	public void calificar(Long clienteId, Long consultaId, boolean util) {
		if (!consultas.calificar(consultaId, clienteId, util)) {
			throw new RecursoNoEncontradoException("No existe la consulta " + consultaId);
		}
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws ConflictoException
	 *             ASISTENTE_SIN_MANUAL si su modelo y año no tienen manual con
	 *             texto (409)
	 * @throws DemasiadosIntentosException
	 *             ASISTENTE_LIMITE si ya hizo sus preguntas del día (429)
	 * @throws ServicioNoDisponibleException
	 *             ASISTENTE_NO_DISPONIBLE si se acabó la cuota del día o el
	 *             proveedor de IA falla (503)
	 */
	public RespuestaAsistenteApp preguntar(Long clienteId, Long vehiculoId, String pregunta) {
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		Manual manual = manualDe(vehiculo).orElseThrow(this::sinManual);

		verificarLimites(clienteId);
		if (!fragmentos.tieneFragmentos(manual.getId()) && indexador.indexarTexto(manual.getId()) == 0) {
			throw sinManual();
		}

		String preguntaLimpia = LimpiadorDatosPersonales.limpiar(pregunta.strip());
		List<FragmentosManual.Fragmento> paginas = buscarPaginas(manual.getId(), preguntaLimpia);
		String auto = vehiculo.modelo() + " " + vehiculo.anio();
		if (paginas.isEmpty()) {
			return guardar(clienteId, vehiculo, preguntaLimpia,
					"No encontré eso en el manual de tu " + auto + ". Puedes preguntarle a tu asesor en la agencia.",
					List.of(), false, PROVEEDOR_NINGUNO);
		}

		ProveedorIA.RespuestaIA respuesta;
		try {
			respuesta = proveedor.responder(INSTRUCCIONES, contenido(vehiculo, preguntaLimpia, paginas));
		} catch (ProveedorIANoDisponibleException ex) {
			throw noDisponible();
		}
		Set<Integer> dadas = paginas.stream().map(FragmentosManual.Fragmento::pagina).collect(Collectors.toSet());
		List<Integer> citadas = respuesta.paginas().stream().filter(dadas::contains).distinct().toList();
		if (respuesta.encontrado() && citadas.isEmpty()) {
			citadas = List.of(paginas.getFirst().pagina());
		}
		List<ConsultasAsistente.Fuente> fuentes = respuesta.encontrado()
				? citadas.stream().map(pagina -> new ConsultasAsistente.Fuente(manual.getId(), pagina)).toList()
				: List.of();
		return guardar(clienteId, vehiculo, preguntaLimpia, respuesta.respuesta(), fuentes, respuesta.encontrado(),
				proveedor.nombre());
	}

	/**
	 * Identifica el testigo de una foto del tablero (V2-M24.05). La foto se limpia
	 * (sin EXIF) y no se guarda.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws SolicitudInvalidaException
	 *             DATOS_INVALIDOS si no es una imagen (400)
	 * @throws DemasiadosIntentosException
	 *             ASISTENTE_LIMITE (429)
	 * @throws ServicioNoDisponibleException
	 *             ASISTENTE_NO_DISPONIBLE (503)
	 */
	public RespuestaTestigoApp identificarTestigo(Long clienteId, Long vehiculoId, byte[] imagen) {
		VehiculoDeCliente vehiculo = vehiculos.buscarPorId(vehiculoId)
				.filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
		byte[] limpia;
		try {
			limpia = LimpiadorImagen.limpiar(imagen);
		} catch (IllegalArgumentException ex) {
			throw new SolicitudInvalidaException("La foto debe ser una imagen JPEG o PNG",
					CodigosError.DATOS_INVALIDOS);
		}
		verificarLimites(clienteId);

		ProveedorIA.RespuestaTestigo visto;
		try {
			visto = proveedor.identificarTestigo(INSTRUCCIONES_TESTIGO, limpia);
		} catch (ProveedorIANoDisponibleException ex) {
			throw noDisponible();
		}
		boolean reconocido = visto.reconocido() && visto.testigo() != null && !visto.testigo().isBlank();
		List<ConsultasAsistente.Fuente> fuentes = reconocido ? paginaDelTestigo(vehiculo, visto.testigo()) : List.of();
		String respuesta = reconocido
				? visto.queHacer()
				: "No reconocí un testigo en la foto. Puedes describirlo con palabras.";
		long id = consultas.guardar(new ConsultasAsistente.NuevaConsulta(clienteId, vehiculo.id(),
				"[foto] " + (reconocido ? visto.testigo() : "sin reconocer"), respuesta, fuentes, reconocido,
				proveedor.nombre()));
		return new RespuestaTestigoApp(id, reconocido, reconocido ? visto.testigo() : null,
				reconocido ? visto.gravedad() : null, reconocido ? visto.queHacer() : null,
				fuentes.stream().map(f -> new RespuestaAsistenteApp.FuenteAsistenteApp(f.manualId(), f.pagina(),
						"Manual, pág. " + f.pagina())).toList());
	}

	/** Primera página del manual que nombra el testigo; vacía si no hay manual. */
	private List<ConsultasAsistente.Fuente> paginaDelTestigo(VehiculoDeCliente vehiculo, String testigo) {
		Optional<Manual> manual = manualDe(vehiculo);
		if (manual.isEmpty()) {
			return List.of();
		}
		long manualId = manual.get().getId();
		if (!fragmentos.tieneFragmentos(manualId) && indexador.indexarTexto(manualId) == 0) {
			return List.of();
		}
		return fragmentos.porPalabras(manualId, testigo, 1).stream()
				.map(f -> new ConsultasAsistente.Fuente(manualId, f.pagina())).toList();
	}

	private void verificarLimites(Long clienteId) {
		ZonedDateTime ahora = ZonedDateTime.now(reloj).withZoneSameInstant(zona);
		Instant inicioDelDia = ahora.toLocalDate().atStartOfDay(zona).toInstant();
		if (consultas.contarDelCliente(clienteId, inicioDelDia) >= limitePorCliente) {
			long segundos = Duration.between(ahora, LocalDate.from(ahora).plusDays(1).atStartOfDay(zona)).toSeconds();
			throw new DemasiadosIntentosException(
					"Ya hiciste tus " + limitePorCliente + " preguntas de hoy. Mañana puedes hacer más.", segundos,
					CodigosError.ASISTENTE_LIMITE);
		}
		if (consultas.contarTodas(inicioDelDia) >= limiteGlobal) {
			throw noDisponible();
		}
	}

	private List<FragmentosManual.Fragmento> buscarPaginas(long manualId, String pregunta) {
		if (fragmentos.tieneVectores(manualId)) {
			try {
				Optional<float[]> vector = proveedor.embeber(pregunta);
				if (vector.isPresent()) {
					return fragmentos.masParecidos(manualId, vector.get(), PAGINAS_DE_CONTEXTO);
				}
			} catch (ProveedorIANoDisponibleException ex) {
				// Sin vector (cuota o falla): se busca por palabras, como dice el ADR 0007.
			}
		}
		return fragmentos.porPalabras(manualId, pregunta, PAGINAS_DE_CONTEXTO);
	}

	private static String contenido(VehiculoDeCliente vehiculo, String pregunta,
			List<FragmentosManual.Fragmento> paginas) {
		StringBuilder texto = new StringBuilder();
		texto.append("Vehículo: ").append(vehiculo.marca() == null ? "" : vehiculo.marca() + " ")
				.append(vehiculo.modelo()).append(' ').append(vehiculo.anio()).append("\n");
		texto.append("Pregunta: ").append(pregunta).append("\n\nFragmentos del manual:\n");
		for (FragmentosManual.Fragmento pagina : paginas) {
			String fragmento = pagina.texto();
			if (fragmento.length() > LARGO_MAXIMO_FRAGMENTO) {
				fragmento = fragmento.substring(0, LARGO_MAXIMO_FRAGMENTO);
			}
			texto.append("[Página ").append(pagina.pagina()).append("]\n").append(fragmento).append("\n\n");
		}
		return texto.toString();
	}

	private RespuestaAsistenteApp guardar(Long clienteId, VehiculoDeCliente vehiculo, String pregunta, String respuesta,
			List<ConsultasAsistente.Fuente> fuentes, boolean encontrado, String nombreProveedor) {
		long id = consultas.guardar(new ConsultasAsistente.NuevaConsulta(clienteId, vehiculo.id(), pregunta, respuesta,
				fuentes, encontrado, nombreProveedor));
		return new RespuestaAsistenteApp(id, respuesta, encontrado, fuentes.stream()
				.map(f -> new FuenteAsistenteApp(f.manualId(), f.pagina(), "Manual, pág. " + f.pagina())).toList());
	}

	private Optional<Manual> manualDe(VehiculoDeCliente vehiculo) {
		return vehiculo.marca() == null
				? manualRepository.sinMarca(vehiculo.modelo(), vehiculo.anio()).stream().findFirst()
				: manualRepository.buscar(vehiculo.marca(), vehiculo.modelo(), vehiculo.anio());
	}

	private ConflictoException sinManual() {
		return new ConflictoException("Tu vehículo aún no tiene un manual que el asistente pueda leer",
				CodigosError.ASISTENTE_SIN_MANUAL);
	}

	private static ServicioNoDisponibleException noDisponible() {
		return new ServicioNoDisponibleException("El asistente no está disponible por ahora. Intenta más tarde.",
				CodigosError.ASISTENTE_NO_DISPONIBLE);
	}
}
