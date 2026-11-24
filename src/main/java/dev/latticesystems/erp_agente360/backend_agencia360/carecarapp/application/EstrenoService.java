package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.ConsultaVendedores;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ModeloCatalogo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoDeEstrenoCreado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.SolicitudEstrenoCreada;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaProspectos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estreno (M16): mensualidad estimada, solicitudes de prueba de manejo y de
 * cotización, y su estado. El prospecto lo crea ventascrm por evento.
 */
@Service
public class EstrenoService {

	public static final String PRUEBA_MANEJO = "PRUEBA_MANEJO";
	public static final String COTIZACION = "COTIZACION";
	private static final Set<String> FRANJAS = Set.of("MANANA", "TARDE");

	private final ModeloCatalogoRepository modelos;
	private final EstimacionTomaCuentaRepository estimaciones;
	private final ParametrosFinanciamiento.Fuente parametros;
	private final SolicitudesEstreno solicitudes;
	private final DirectorioClientes clientes;
	private final ConsultaProspectos prospectos;
	private final ConsultaVendedores vendedores;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public EstrenoService(ModeloCatalogoRepository modelos, EstimacionTomaCuentaRepository estimaciones,
			ParametrosFinanciamiento.Fuente parametros, SolicitudesEstreno solicitudes, DirectorioClientes clientes,
			ConsultaProspectos prospectos, ConsultaVendedores vendedores, ApplicationEventPublisher eventos) {
		this(modelos, estimaciones, parametros, solicitudes, clientes, prospectos, vendedores, eventos,
				Clock.systemUTC(), ZoneId.of("America/Mexico_City"));
	}

	EstrenoService(ModeloCatalogoRepository modelos, EstimacionTomaCuentaRepository estimaciones,
			ParametrosFinanciamiento.Fuente parametros, SolicitudesEstreno solicitudes, DirectorioClientes clientes,
			ConsultaProspectos prospectos, ConsultaVendedores vendedores, ApplicationEventPublisher eventos,
			Clock reloj, ZoneId zona) {
		this.modelos = modelos;
		this.estimaciones = estimaciones;
		this.parametros = parametros;
		this.solicitudes = solicitudes;
		this.clientes = clientes;
		this.prospectos = prospectos;
		this.vendedores = vendedores;
		this.eventos = eventos;
		this.reloj = reloj;
		this.zona = zona;
	}

	/** Lo que pide la app al crear una solicitud. */
	public record PedidoEstreno(String tipo, Long modeloCatalogoId, LocalDate preferenciaFecha,
			String preferenciaFranja, BigDecimal enganche, Integer plazoMeses, Long estimacionTomaCuentaId) {
	}

	/** Una solicitud como la ve la app, con su estado. */
	public record SolicitudEstrenoApp(long id, String tipo, long modeloCatalogoId, String modelo, String estado,
			String vendedor, LocalDate preferenciaFecha, String preferenciaFranja,
			CalculadoraMensualidad.Cotizacion cotizacion, Long estimacionTomaCuentaId, java.time.Instant creadaEn) {
	}

	/**
	 * V2-M16.02 · No guarda nada.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el modelo no existe o no está activo (404)
	 * @throws ConflictoException
	 *             ESTRENO_SIN_PRECIO (409)
	 * @throws IllegalArgumentException
	 *             enganche bajo o plazo no permitido (400)
	 */
	@Transactional(readOnly = true)
	public CalculadoraMensualidad.Cotizacion calcular(Long modeloCatalogoId, BigDecimal enganche, Integer plazoMeses) {
		return cotizar(modeloActivo(modeloCatalogoId), enganche, plazoMeses);
	}

	/**
	 * V2-M16.01, V2-M16.03 · Guarda la solicitud y avisa a ventascrm.
	 *
	 * @throws RecursoNoEncontradoException
	 *             modelo o estimación que no son del cliente (404)
	 * @throws ConflictoException
	 *             ESTRENO_DUPLICADA o ESTRENO_SIN_PRECIO (409)
	 * @throws IllegalArgumentException
	 *             datos que no corresponden al tipo (400)
	 */
	@Transactional
	public SolicitudEstrenoApp pedir(Long clienteId, PedidoEstreno pedido) {
		String tipo = pedido.tipo();
		if (!PRUEBA_MANEJO.equals(tipo) && !COTIZACION.equals(tipo)) {
			throw new IllegalArgumentException("El tipo debe ser PRUEBA_MANEJO o COTIZACION");
		}
		ModeloCatalogo modelo = modeloActivo(pedido.modeloCatalogoId());
		Long estimacion = pedido.estimacionTomaCuentaId();
		if (estimacion != null
				&& estimaciones.findById(estimacion).filter(e -> e.getClienteId().equals(clienteId)).isEmpty()) {
			throw new RecursoNoEncontradoException("No existe la estimación " + estimacion);
		}
		if (hayAbierta(clienteId, modelo.getId(), tipo)) {
			throw new ConflictoException("Ya pediste esto para este modelo; un asesor te contactará",
					CodigosError.ESTRENO_DUPLICADA);
		}

		SolicitudesEstreno.NuevaSolicitud nueva;
		if (PRUEBA_MANEJO.equals(tipo)) {
			LocalDate manana = LocalDate.now(reloj.withZone(zona)).plusDays(1);
			if (pedido.preferenciaFecha() == null || pedido.preferenciaFecha().isBefore(manana)) {
				throw new IllegalArgumentException("Elige un día a partir de mañana");
			}
			if (!FRANJAS.contains(pedido.preferenciaFranja())) {
				throw new IllegalArgumentException("Elige mañana o tarde");
			}
			nueva = new SolicitudesEstreno.NuevaSolicitud(clienteId, modelo.getId(), tipo, pedido.preferenciaFecha(),
					pedido.preferenciaFranja(), null, null, null, null, estimacion);
		} else {
			CalculadoraMensualidad.Cotizacion c = cotizar(modelo, pedido.enganche(), pedido.plazoMeses());
			nueva = new SolicitudesEstreno.NuevaSolicitud(clienteId, modelo.getId(), tipo, null, null, c.enganche(),
					c.plazoMeses(), c.mensualidad(), c.tasaAnual(), estimacion);
		}
		long id = solicitudes.guardar(nueva);
		DirectorioClientes.ClienteRegistrado cliente = clientes.buscarPorId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));
		eventos.publishEvent(new SolicitudEstrenoCreada(id, cliente.nombre(), cliente.correo(), null, tipo));
		return comoApp(solicitudes.buscar(id));
	}

	/** V2-M16.04 · Más recientes primero. */
	@Transactional(readOnly = true)
	public Pagina<SolicitudEstrenoApp> delCliente(Long clienteId, Integer pagina, Integer tamano) {
		var pedida = Pagina.pedir(pagina, tamano);
		long total = solicitudes.contarDelCliente(clienteId);
		List<SolicitudEstrenoApp> contenido = solicitudes
				.delCliente(clienteId, pedida.getPageNumber(), pedida.getPageSize()).stream().map(this::comoApp)
				.toList();
		int paginas = (int) Math.ceil((double) total / pedida.getPageSize());
		return new Pagina<>(contenido, pedida.getPageNumber(), pedida.getPageSize(), total, paginas);
	}

	@ApplicationModuleListener
	void alCrearseProspecto(ProspectoDeEstrenoCreado creado) {
		solicitudes.ligarProspecto(creado.solicitudId(), creado.prospectoId());
	}

	private boolean hayAbierta(long clienteId, long modeloId, String tipo) {
		return solicitudes.iguales(clienteId, modeloId, tipo).stream().anyMatch(s -> s.prospectoId() == null
				|| prospectos.estado(s.prospectoId()).map(e -> !e.terminado()).orElse(true));
	}

	private ModeloCatalogo modeloActivo(Long id) {
		return modelos.findById(id == null ? -1L : id).filter(ModeloCatalogo::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el modelo " + id));
	}

	private CalculadoraMensualidad.Cotizacion cotizar(ModeloCatalogo modelo, BigDecimal enganche, Integer plazo) {
		BigDecimal precio = modelo.getPrecioDesde();
		if (precio == null || precio.signum() <= 0) {
			throw new ConflictoException("Este modelo aún no tiene precio publicado; pregúntale a un asesor",
					CodigosError.ESTRENO_SIN_PRECIO);
		}
		ParametrosFinanciamiento p = parametros.actuales();
		if (plazo == null || !p.plazos().contains(plazo)) {
			throw new IllegalArgumentException("El plazo debe ser uno de " + p.plazos());
		}
		BigDecimal minimo = precio.multiply(p.engancheMinimoPct()).setScale(2, RoundingMode.UP);
		if (enganche == null || enganche.compareTo(minimo) < 0 || enganche.compareTo(precio) >= 0) {
			throw new IllegalArgumentException("El enganche debe ser de al menos $" + minimo + " y menor al precio");
		}
		return CalculadoraMensualidad.calcular(precio, enganche, plazo, p.tasaAnual());
	}

	private SolicitudEstrenoApp comoApp(SolicitudesEstreno.Solicitud s) {
		String estado = "RECIBIDA";
		String vendedor = null;
		if (s.prospectoId() != null) {
			var e = prospectos.estado(s.prospectoId());
			if (e.isPresent()) {
				estado = e.get().terminado() ? "CERRADA" : "EN_ATENCION";
				vendedor = vendedores.nombreDe(e.get().vendedorId()).orElse(null);
			}
		}
		CalculadoraMensualidad.Cotizacion cotizacion = null;
		if (COTIZACION.equals(s.tipo()) && s.enganche() != null) {
			BigDecimal precio = modelos.findById(s.modeloCatalogoId()).map(ModeloCatalogo::getPrecioDesde)
					.orElse(s.enganche());
			cotizacion = new CalculadoraMensualidad.Cotizacion(precio, s.enganche(),
					precio.subtract(s.enganche()).setScale(2, RoundingMode.HALF_UP), s.tasaAnual(), s.plazoMeses(),
					s.mensualidad(), CalculadoraMensualidad.LEYENDA);
		}
		return new SolicitudEstrenoApp(s.id(), s.tipo(), s.modeloCatalogoId(), s.modelo(), estado, vendedor,
				s.preferenciaFecha(), s.preferenciaFranja(), cotizacion, s.estimacionTomaCuentaId(), s.creadaEn());
	}
}
