package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EntregaPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoCancelacionPedido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoRenglon;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PedidoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ApartadoDeRefacciones;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.ApartadoDeRefacciones.PiezaApartada;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.Auto;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.FiltroDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CatalogoDeTienda.ProductoDeTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.CategoriaTienda;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La tienda de la app (V2-M14.01 a V2-M14.03 y V2-M14.05): catálogo compatible,
 * apartados, instalación en una cita y estados del pedido.
 */
@Service
public class TiendaAppService {

	static final String EVENTO_ESTADO = "pedido.estado";
	private static final int TEXTO_MAXIMO = 80;
	private static final int LLAVE_MINIMA = 8;
	private static final int LLAVE_MAXIMA = 64;
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
	private static final String SIN_PEDIDO = "No existe el pedido ";

	/**
	 * @param creado
	 *            {@code false} si la llave ya había creado ese pedido
	 */
	public record Apartado(PedidoVista pedido, boolean creado) {
	}

	private final PedidoAppRepository pedidos;
	private final CitaRepository citas;
	private final VehiculosDeClientes vehiculos;
	private final CatalogoDeTienda catalogo;
	private final ApartadoDeRefacciones inventario;
	private final ApplicationEventPublisher eventos;
	private final Duration vigencia;
	private final Clock reloj;

	@Autowired
	public TiendaAppService(PedidoAppRepository pedidos, CitaRepository citas, VehiculosDeClientes vehiculos,
			CatalogoDeTienda catalogo, ApartadoDeRefacciones inventario, ApplicationEventPublisher eventos,
			@Value("${app.tienda.apartado.vigencia:PT48H}") Duration vigencia) {
		this(pedidos, citas, vehiculos, catalogo, inventario, eventos, vigencia,
				Clock.system(AgendaAppService.ZONA_AGENCIA));
	}

	TiendaAppService(PedidoAppRepository pedidos, CitaRepository citas, VehiculosDeClientes vehiculos,
			CatalogoDeTienda catalogo, ApartadoDeRefacciones inventario, ApplicationEventPublisher eventos,
			Duration vigencia, Clock reloj) {
		this.pedidos = pedidos;
		this.citas = citas;
		this.vehiculos = vehiculos;
		this.catalogo = catalogo;
		this.inventario = inventario;
		this.eventos = eventos;
		this.vigencia = vigencia;
		this.reloj = reloj;
	}

	/**
	 * Productos publicados; con vehículo, solo los que le quedan (V2-M14.01).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si la categoría, el texto o la página no son válidos (400)
	 */
	@Transactional(readOnly = true)
	public Pagina<ProductoApp> productos(Long clienteId, Long vehiculoId, String categoria, String texto,
			Integer pagina, Integer tamano) {
		Pageable pedida = Pagina.pedir(pagina, tamano);
		if (texto != null && texto.length() > TEXTO_MAXIMO) {
			throw new IllegalArgumentException("La búsqueda admite hasta 80 caracteres");
		}
		Auto auto = vehiculoId == null ? null : auto(clienteId, vehiculoId);
		FiltroDeTienda filtro = new FiltroDeTienda(auto, categoria(categoria), texto);
		return Pagina.de(catalogo.buscar(filtro, pedida),
				producto -> ProductoApp.de(producto, auto == null ? null : Boolean.TRUE));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el producto no está publicado o el vehículo no es del cliente
	 *             (404)
	 */
	@Transactional(readOnly = true)
	public ProductoApp producto(Long clienteId, Long productoId, Long vehiculoId) {
		Auto auto = vehiculoId == null ? null : auto(clienteId, vehiculoId);
		ProductoDeTienda producto = catalogo.publicado(productoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto " + productoId));
		return ProductoApp.de(producto, auto == null ? null : producto.leQuedaA(auto));
	}

	/**
	 * Aparta todas las piezas o ninguna (V2-M14.02).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si un producto no está publicado o la cita no es del cliente
	 *             (404)
	 * @throws ConflictoException
	 *             con {@code SIN_EXISTENCIA}, {@code CITA_NO_DISPONIBLE} o
	 *             {@code LIMITE_APARTADOS} (409)
	 * @throws NoProcesableException
	 *             con {@code PRODUCTO_INCOMPATIBLE} (422)
	 * @throws IllegalArgumentException
	 *             si la petición no es válida (400)
	 */
	@Transactional
	public Apartado apartar(Long clienteId, String llave, NuevoPedido nuevo) {
		String llaveLimpia = llave(llave);
		Map<Long, Integer> cantidades = cantidades(nuevo);
		pedidos.bloquearCliente(clienteId);
		Optional<PedidoApp> repetido = pedidos.findByClienteIdAndLlaveIdempotencia(clienteId, llaveLimpia);
		if (repetido.isPresent()) {
			return new Apartado(ver(clienteId, repetido.get().getId()), false);
		}
		ponerAlDia(clienteId);
		if (pedidos.countByClienteIdAndEstadoIn(clienteId,
				PedidoApp.estadosAbiertos()) >= ReglasTienda.PEDIDOS_ABIERTOS_MAXIMOS) {
			throw new ConflictoException("Ya tienes 3 pedidos sin entregar", CodigosError.LIMITE_APARTADOS);
		}
		Map<Long, ProductoDeTienda> productos = publicados(cantidades);
		Cita cita = null;
		if (nuevo.entrega() == EntregaPedido.CITA) {
			cita = citaParaInstalar(clienteId, nuevo.citaId());
			exigirCompatibles(clienteId, cita, productos.values().stream().toList());
		}
		PedidoApp pedido = PedidoApp.apartar(clienteId, llaveLimpia, cita == null ? null : cita.getId(), vence(cita));
		// Se guarda antes de mover el inventario porque cada movimiento lo referencia.
		pedidos.saveAndFlush(pedido);
		List<PiezaApartada> apartadas = inventario.apartar(pedido.getId(), cantidades);
		apartadas.forEach(pieza -> pedido.agregar(pieza.productoId(), pieza.cantidad(), pieza.precio()));
		avisar(pedido);
		return new Apartado(vista(pedido, false), true);
	}

	/** El más reciente primero (V2-M14.05). */
	@Transactional
	public Pagina<PedidoVista> pedidos(Long clienteId, Integer pagina, Integer tamano) {
		Pageable pedida = Pagina.pedir(pagina, tamano);
		ponerAlDia(clienteId);
		return Pagina.de(pedidos.findByClienteIdOrderByCreatedAtDescIdDesc(clienteId, pedida),
				pedido -> vista(pedido, false));
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 */
	@Transactional
	public PedidoVista ver(Long clienteId, Long pedidoId) {
		return vista(propio(clienteId, pedidoId), false);
	}

	/**
	 * Cancela y libera las piezas; repetirlo no cambia nada (V2-M14.02).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si no existe o no es del cliente (404)
	 * @throws ConflictoException
	 *             con {@code PEDIDO_NO_CANCELABLE} si ya se entregó (409)
	 */
	@Transactional
	public PedidoVista cancelar(Long clienteId, Long pedidoId) {
		PedidoApp pedido = propio(clienteId, pedidoId);
		if (pedido.getEstado() != EstadoPedido.CANCELADO) {
			cancelarYLiberar(pedido, MotivoCancelacionPedido.CLIENTE);
		}
		return vista(pedido, false);
	}

	/**
	 * Elige la cita donde se instala o, con {@code citaId} nulo, vuelve a mostrador
	 * (V2-M14.03).
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el pedido o la cita no son del cliente (404)
	 * @throws ConflictoException
	 *             con {@code CITA_NO_DISPONIBLE} o {@code PEDIDO_NO_MODIFICABLE}
	 *             (409)
	 * @throws NoProcesableException
	 *             con {@code PRODUCTO_INCOMPATIBLE} (422)
	 */
	@Transactional
	public PedidoVista instalar(Long clienteId, Long pedidoId, Long citaId) {
		PedidoApp pedido = propio(clienteId, pedidoId);
		if (!pedido.abierto()) {
			throw new ConflictoException("El pedido ya no se puede cambiar", CodigosError.PEDIDO_NO_MODIFICABLE);
		}
		Cita cita = null;
		if (citaId != null) {
			cita = citaParaInstalar(clienteId, citaId);
			exigirCompatibles(clienteId, cita, catalogo.porIds(pedido.cantidades().keySet()));
		}
		if (!Objects.equals(pedido.getCitaId(), citaId)) {
			pedido.instalarEn(citaId, vence(cita));
		}
		return vista(pedido, false);
	}

	/** Para el personal, los más antiguos primero. */
	@Transactional
	public Pagina<PedidoVista> delPersonal(EstadoPedido estado, Integer pagina, Integer tamano) {
		Pageable pedida = Pagina.pedir(pagina, tamano);
		return Pagina.de(
				pedidos.findByEstadoInOrderByCreatedAtAscIdAsc(
						estado == null ? PedidoApp.estadosAbiertos() : List.of(estado), pedida),
				pedido -> vista(pedido, true));
	}

	@Transactional
	public PedidoVista marcarListo(Long pedidoId) {
		PedidoApp pedido = alDia(pedidoId);
		pedido.marcarListo(Instant.now(reloj), vigencia);
		avisar(pedido);
		return vista(pedido, true);
	}

	@Transactional
	public PedidoVista entregar(Long pedidoId) {
		PedidoApp pedido = alDia(pedidoId);
		pedido.entregar(Instant.now(reloj));
		avisar(pedido);
		return vista(pedido, true);
	}

	@Transactional
	public PedidoVista cancelarPorLaAgencia(Long pedidoId) {
		PedidoApp pedido = alDia(pedidoId);
		if (pedido.getEstado() != EstadoPedido.CANCELADO) {
			cancelarYLiberar(pedido, MotivoCancelacionPedido.AGENCIA);
		}
		return vista(pedido, true);
	}

	/** Ids de los pedidos que ya vencieron, para el proceso programado. */
	@Transactional(readOnly = true)
	public List<Long> idsVencidos() {
		return pedidos.idsVencidos(PedidoApp.estadosAbiertos(), Instant.now(reloj));
	}

	/** Cancela el pedido si venció; cada uno va en su transacción. */
	@Transactional
	public void vencer(Long pedidoId) {
		pedidos.bloquear(pedidoId).ifPresent(this::vencerSiToca);
	}

	private void ponerAlDia(Long clienteId) {
		pedidos.idsVencidosDelCliente(clienteId, PedidoApp.estadosAbiertos(), Instant.now(reloj)).forEach(this::vencer);
	}

	private void vencerSiToca(PedidoApp pedido) {
		if (pedido.vencido(Instant.now(reloj))) {
			cancelarYLiberar(pedido, MotivoCancelacionPedido.VENCIDO);
		}
	}

	private void cancelarYLiberar(PedidoApp pedido, MotivoCancelacionPedido motivo) {
		pedido.cancelar(motivo, Instant.now(reloj));
		inventario.liberar(pedido.getId(), pedido.cantidades());
		avisar(pedido);
	}

	/** El pedido del cliente, bloqueado y con su vencimiento al día. */
	private PedidoApp propio(Long clienteId, Long pedidoId) {
		if (!pedidos.existsByIdAndClienteId(pedidoId, clienteId)) {
			throw new RecursoNoEncontradoException(SIN_PEDIDO + pedidoId);
		}
		return alDia(pedidoId);
	}

	private PedidoApp alDia(Long pedidoId) {
		PedidoApp pedido = pedidos.bloquear(pedidoId)
				.orElseThrow(() -> new RecursoNoEncontradoException(SIN_PEDIDO + pedidoId));
		vencerSiToca(pedido);
		return pedido;
	}

	private void avisar(PedidoApp pedido) {
		eventos.publishEvent(new AvisoAlCliente(pedido.getClienteId(), EVENTO_ESTADO,
				Map.of("pedidoId", pedido.getId(), "estado", minusculas(pedido.getEstado()))));
	}

	private Auto auto(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(propio -> propio.propietario().id().equals(clienteId))
				.map(vehiculo -> new Auto(vehiculo.modelo(), vehiculo.anio()))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
	}

	private Map<Long, ProductoDeTienda> publicados(Map<Long, Integer> cantidades) {
		Map<Long, ProductoDeTienda> productos = new LinkedHashMap<>();
		cantidades.keySet().forEach(id -> productos.put(id, catalogo.publicado(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto " + id))));
		return productos;
	}

	private Cita citaParaInstalar(Long clienteId, Long citaId) {
		Cita cita = citas.findByIdAndClienteId(citaId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita " + citaId));
		if (cita.getEstado() != EstadoCita.CONFIRMADA
				|| !cita.getFecha().atTime(cita.getHoraInicio()).isAfter(LocalDateTime.now(reloj))) {
			throw new ConflictoException("Esa cita está cancelada o ya pasó", CodigosError.CITA_NO_DISPONIBLE);
		}
		return cita;
	}

	private void exigirCompatibles(Long clienteId, Cita cita, List<ProductoDeTienda> productos) {
		Auto auto = auto(clienteId, cita.getVehiculoClienteId());
		productos.stream().filter(producto -> !producto.leQuedaA(auto)).findFirst().ifPresent(producto -> {
			throw new NoProcesableException(producto.nombre() + " no le queda al auto de esa cita",
					CodigosError.PRODUCTO_INCOMPATIBLE);
		});
	}

	private Instant vence(Cita cita) {
		Instant enMostrador = ReglasTienda.venceEnMostrador(Instant.now(reloj), vigencia);
		return cita == null ? enMostrador : ReglasTienda.venceConCita(cita.getFecha());
	}

	private PedidoVista vista(PedidoApp pedido, boolean paraElPersonal) {
		Map<Long, ProductoDeTienda> productos = catalogo.porIds(pedido.cantidades().keySet()).stream()
				.collect(Collectors.toMap(ProductoDeTienda::id, Function.identity()));
		List<PedidoVista.Renglon> renglones = pedido.getRenglones().stream()
				.map(renglon -> renglon(renglon, productos.get(renglon.getRefaccionId()))).toList();
		PedidoVista.CitaDePedido cita = pedido.getCitaId() == null
				? null
				: citas.findById(pedido.getCitaId())
						.map(encontrada -> new PedidoVista.CitaDePedido(encontrada.getId(), encontrada.getFecha(),
								HORA.format(encontrada.getHoraInicio()), encontrada.getVehiculoClienteId()))
						.orElse(null);
		return new PedidoVista(pedido.getId(), minusculas(pedido.getEstado()), minusculas(pedido.getEntrega()),
				pedido.getTotal(), pedido.getCreatedAt(), pedido.getVenceEn(), pedido.getListoEn(),
				pedido.getEntregadoEn(), pedido.getCanceladoEn(),
				pedido.getMotivoCancelacion() == null ? null : minusculas(pedido.getMotivoCancelacion()), cita,
				renglones, paraElPersonal ? pedido.getClienteId() : null);
	}

	private static PedidoVista.Renglon renglon(PedidoRenglon renglon, ProductoDeTienda producto) {
		return new PedidoVista.Renglon(renglon.getRefaccionId(), producto.codigo(), producto.nombre(),
				renglon.getCantidad(), renglon.getPrecio(), renglon.getImporte());
	}

	private static String llave(String llave) {
		String limpia = llave == null ? "" : llave.strip();
		if (limpia.length() < LLAVE_MINIMA || limpia.length() > LLAVE_MAXIMA) {
			throw new IllegalArgumentException("La llave Idempotency-Key debe tener entre 8 y 64 caracteres");
		}
		return limpia;
	}

	private static Map<Long, Integer> cantidades(NuevoPedido nuevo) {
		if (nuevo.entrega() == null || nuevo.renglones() == null || nuevo.renglones().isEmpty()
				|| nuevo.renglones().size() > ReglasTienda.RENGLONES_MAXIMOS) {
			throw new IllegalArgumentException("Indica la entrega y de 1 a 10 productos");
		}
		if ((nuevo.entrega() == EntregaPedido.CITA) != (nuevo.citaId() != null)) {
			throw new IllegalArgumentException("La cita va solo cuando la entrega es en una cita");
		}
		Map<Long, Integer> cantidades = new LinkedHashMap<>();
		for (NuevoPedido.Pieza pieza : nuevo.renglones()) {
			if (pieza.productoId() == null || pieza.cantidad() < 1
					|| pieza.cantidad() > ReglasTienda.PIEZAS_MAXIMAS_POR_RENGLON) {
				throw new IllegalArgumentException("Cada producto lleva de 1 a 10 piezas");
			}
			if (cantidades.put(pieza.productoId(), pieza.cantidad()) != null) {
				throw new IllegalArgumentException("Cada producto va una sola vez");
			}
		}
		return cantidades;
	}

	private static CategoriaTienda categoria(String categoria) {
		if (categoria == null) {
			return null;
		}
		for (CategoriaTienda posible : CategoriaTienda.values()) {
			if (minusculas(posible).equals(categoria)) {
				return posible;
			}
		}
		throw new IllegalArgumentException("La categoría no existe");
	}

	private static String minusculas(Enum<?> valor) {
		return valor.name().toLowerCase(Locale.ROOT);
	}
}
