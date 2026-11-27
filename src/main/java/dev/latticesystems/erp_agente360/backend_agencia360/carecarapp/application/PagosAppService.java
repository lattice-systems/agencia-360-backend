package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ProcesadorPagos.Resultado;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MedioPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MetodoPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MotivoFalloPago;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PagoApp.Cobrable;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PedidoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.PerfilFiscal;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TarjetaDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.MetodoPagoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PagoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PedidoAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.PerfilFiscalRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.HistorialDeServicios.DetalleDelServicio;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TallerEnVivo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagos de órdenes de servicio y de pedidos de la tienda desde la app, con
 * tarjeta o billetera, y tarjetas guardadas (V2-M08.02, V2-M08.03, V2-M08.04,
 * V2-M08.07 y V2-M14.04), con el procesador simulado de la ADR 0009. Cada
 * intento lleva su llave de idempotencia: repetirla devuelve el mismo intento y
 * no cobra dos veces. Una orden se paga una sola vez y el personal también la
 * puede marcar pagada en caja. Un pedido también se paga una sola vez y su
 * candado es el de su fila. Lo ajeno responde 404.
 */
@Service
public class PagosAppService {

	static final String EVENTO = "pago.confirmado";
	static final int TARJETAS_MAXIMAS = 10;
	static final Duration VIGENCIA_AUTENTICACION = Duration.ofMinutes(15);
	static final int LLAVE_MINIMA = 8;
	static final int LLAVE_MAXIMA = 64;
	static final int REFERENCIA_MAXIMA = 40;
	static final int ANIOS_DE_VIGENCIA_MAXIMA = 20;

	/** Separa las claves de bloqueo de los cobros de una orden. */
	private static final long PREFIJO_ORDEN = 0x5041_474F_0000_0000L;

	private static final String SIN_PEDIDO = "No existe el pedido ";

	/** Separa las claves de bloqueo de las tarjetas de un cliente. */
	private static final long PREFIJO_TARJETAS = 0x5441_524A_0000_0000L;

	private final PagoAppRepository pagos;
	private final MetodoPagoRepository metodos;
	private final PerfilFiscalRepository perfiles;
	private final ProcesadorPagos procesador;
	private final HistorialDeServicios historial;
	private final TallerEnVivo taller;
	private final VehiculosDeClientes vehiculos;
	private final PedidoAppRepository pedidos;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public PagosAppService(PagoAppRepository pagos, MetodoPagoRepository metodos, PerfilFiscalRepository perfiles,
			ProcesadorPagos procesador, HistorialDeServicios historial, TallerEnVivo taller,
			VehiculosDeClientes vehiculos, PedidoAppRepository pedidos, ApplicationEventPublisher eventos) {
		this(pagos, metodos, perfiles, procesador, historial, taller, vehiculos, pedidos, eventos, Clock.systemUTC(),
				ZoneId.of("America/Mexico_City"));
	}

	PagosAppService(PagoAppRepository pagos, MetodoPagoRepository metodos, PerfilFiscalRepository perfiles,
			ProcesadorPagos procesador, HistorialDeServicios historial, TallerEnVivo taller,
			VehiculosDeClientes vehiculos, PedidoAppRepository pedidos, ApplicationEventPublisher eventos, Clock reloj,
			ZoneId zona) {
		this.pagos = pagos;
		this.metodos = metodos;
		this.perfiles = perfiles;
		this.procesador = procesador;
		this.historial = historial;
		this.taller = taller;
		this.vehiculos = vehiculos;
		this.pedidos = pedidos;
		this.eventos = eventos;
		this.reloj = reloj;
		this.zona = zona;
	}

	/**
	 * El intento y si se creó ahora.
	 *
	 * @param creado
	 *            {@code false} si la llave ya había creado ese intento
	 */
	public record Cobro(PagoVista pago, boolean creado) {
	}

	/**
	 * Cobra la orden (V2-M08.02). El intento queda {@code PAGADO}, {@code FALLIDO}
	 * con su motivo o {@code PENDIENTE} si el banco simulado pide 3-D Secure.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la orden, la tarjeta guardada o el perfil fiscal no existen o
	 *             son de otro cliente (404)
	 * @throws ConflictoException
	 *             con {@code ORDEN_NO_PAGABLE} o {@code PAGO_EN_CURSO} (409)
	 * @throws IllegalArgumentException
	 *             si la llave, la tarjeta o el vencimiento no son válidos (400)
	 */
	@Transactional
	public Cobro pagar(Long clienteId, Long ordenId, String llave, NuevoPago nuevo) {
		String llaveLimpia = llave(llave);
		exigirUnMedio(nuevo);
		pagos.bloquear(PREFIJO_ORDEN | ordenId);
		Optional<PagoApp> repetido = pagos.findByClienteIdAndLlaveIdempotencia(clienteId, llaveLimpia);
		if (repetido.isPresent()) {
			if (!Objects.equals(repetido.get().getOrdenId(), ordenId)) {
				throw new IllegalArgumentException("Esa llave Idempotency-Key ya se usó para pagar otra orden");
			}
			return new Cobro(PagoVista.de(repetido.get()), false);
		}
		Cobrable orden = cobrable(clienteId, ordenId);
		return cobrar(clienteId, orden, llaveLimpia, nuevo, reloj.instant());
	}

	/**
	 * Cobra el total de un pedido de la tienda (V2-M14.04) igual que una orden. Al
	 * quedar pagado ya no vence y solo lo cancela la agencia.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el pedido, la tarjeta guardada o el perfil fiscal no existen o
	 *             son de otro cliente (404)
	 * @throws ConflictoException
	 *             con {@code PEDIDO_NO_PAGABLE} o {@code PAGO_EN_CURSO} (409)
	 * @throws IllegalArgumentException
	 *             si la llave, la tarjeta o el vencimiento no son válidos, o la
	 *             llave ya pagó otra orden u otro pedido (400)
	 */
	@Transactional
	public Cobro pagarPedido(Long clienteId, Long pedidoId, String llave, NuevoPago nuevo) {
		String llaveLimpia = llave(llave);
		exigirUnMedio(nuevo);
		if (!pedidos.existsByIdAndClienteId(pedidoId, clienteId)) {
			throw new RecursoNoEncontradoException(SIN_PEDIDO + pedidoId);
		}
		PedidoApp pedido = pedidos.bloquear(pedidoId)
				.orElseThrow(() -> new RecursoNoEncontradoException(SIN_PEDIDO + pedidoId));
		Optional<PagoApp> repetido = pagos.findByClienteIdAndLlaveIdempotencia(clienteId, llaveLimpia);
		if (repetido.isPresent()) {
			if (!pedidoId.equals(repetido.get().getPedidoId())) {
				throw new IllegalArgumentException(
						"Esa llave Idempotency-Key ya se usó para pagar otra orden o pedido");
			}
			return new Cobro(PagoVista.de(repetido.get()), false);
		}
		Instant ahora = reloj.instant();
		pedido.exigirPagable(ahora);
		return cobrar(clienteId, Cobrable.dePedido(pedidoId, clienteId, pedido.getTotal()), llaveLimpia, nuevo, ahora);
	}

	/** Lo común de cobrar una orden o un pedido, ya bajo su candado. */
	private Cobro cobrar(Long clienteId, Cobrable orden, String llaveLimpia, NuevoPago nuevo, Instant ahora) {
		cerrarPendiente(orden, ahora, null);
		Long perfilFiscalId = perfilFiscal(clienteId, nuevo.perfilFiscalId());
		if (nuevo.billetera() != null) {
			TarjetaDePrueba billetera = TarjetaDePrueba.deBilletera(nuevo.billetera());
			PagoApp pago = PagoApp.conBilletera(orden, procesador.nombre(), procesador.nuevaIntencion(), llaveLimpia,
					nuevo.billetera(), billetera, perfilFiscalId, ahora);
			pagos.saveAndFlush(pago);
			aplicar(pago, billetera, procesador.cobrar(pago.getIntencionId(), billetera, orden.monto()), ahora);
			return new Cobro(PagoVista.de(pago), true);
		}
		Long metodoPagoId = null;
		TarjetaDePrueba tarjeta;
		YearMonth vence;
		if (nuevo.metodoPagoId() != null) {
			MetodoPago guardada = tarjetaDelCliente(clienteId, nuevo.metodoPagoId());
			if (guardada.vencida(hoy())) {
				throw new IllegalArgumentException("La tarjeta guardada ya venció");
			}
			metodoPagoId = guardada.getId();
			tarjeta = procesador.tarjeta(guardada.getTokenProcesador()).orElseThrow(
					() -> new IllegalArgumentException("El procesador ya no reconoce la tarjeta guardada"));
			vence = guardada.getVence();
		} else {
			tarjeta = procesador.tarjeta(nuevo.token().strip())
					.orElseThrow(() -> new IllegalArgumentException("Esa tarjeta no es una de las tarjetas de prueba"));
			vence = vencimiento(nuevo.mesVence(), nuevo.anioVence());
		}
		PagoApp pago = PagoApp.conTarjeta(orden, procesador.nombre(), procesador.nuevaIntencion(), llaveLimpia, tarjeta,
				vence, metodoPagoId, nuevo.guardarTarjeta(), perfilFiscalId, ahora);
		pagos.saveAndFlush(pago);
		aplicar(pago, tarjeta, procesador.cobrar(pago.getIntencionId(), tarjeta, orden.monto()), ahora);
		return new Cobro(PagoVista.de(pago), true);
	}

	/**
	 * Responde el 3-D Secure de un pago pendiente. Si ya venció, queda
	 * {@code FALLIDO} con {@code AUTENTICACION_VENCIDA}.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el pago no existe o es de otro cliente (404)
	 * @throws ConflictoException
	 *             con {@code PAGO_NO_PENDIENTE} si ya no espera el 3-D Secure (409)
	 */
	@Transactional
	public PagoVista autenticar(Long clienteId, Long pagoId, boolean confirmada) {
		Long ordenId = pagos.ordenDelPago(pagoId, clienteId).orElse(null);
		PedidoApp pedido = null;
		if (ordenId != null) {
			pagos.bloquear(PREFIJO_ORDEN | ordenId);
		} else {
			Long pedidoId = pagos.pedidoDelPago(pagoId, clienteId)
					.orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + pagoId));
			pedido = pedidos.bloquear(pedidoId).orElseThrow();
		}
		PagoApp pago = pagos.findByIdAndClienteId(pagoId, clienteId).orElseThrow();
		if (!pago.requiereAutenticacion()) {
			throw new ConflictoException("El pago ya no espera la confirmación de tu banco",
					CodigosError.PAGO_NO_PENDIENTE);
		}
		Instant ahora = reloj.instant();
		if (pago.vencido(ahora, VIGENCIA_AUTENTICACION)) {
			pago.fallar(MotivoFalloPago.AUTENTICACION_VENCIDA, ahora);
			return PagoVista.de(pago);
		}
		if (pedido != null && !pedido.abierto()) {
			throw new ConflictoException("La agencia ya canceló el pedido", CodigosError.PEDIDO_NO_PAGABLE);
		}
		TarjetaDePrueba tarjeta = procesador.tarjeta(pago.getTokenProcesador())
				.orElseThrow(() -> new IllegalStateException("El procesador ya no reconoce la tarjeta del pago"));
		aplicar(pago, tarjeta, procesador.autenticar(pago.getIntencionId(), tarjeta, confirmada), ahora);
		return PagoVista.de(pago);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el pago no existe o es de otro cliente (404)
	 */
	@Transactional
	public PagoVista pago(Long clienteId, Long pagoId) {
		vencerPendientes(clienteId);
		return pagos.findByIdAndClienteId(pagoId, clienteId).map(PagoVista::de)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + pagoId));
	}

	/** Mis pagos, del más nuevo al más viejo (V2-M08.07). */
	@Transactional
	public Pagina<PagoVista> pagos(Long clienteId, Integer pagina, Integer tamano) {
		var pedida = Pagina.pedir(pagina, tamano);
		vencerPendientes(clienteId);
		return Pagina.de(pagos.findByClienteIdOrderByCreadoEnDescIdDesc(clienteId, pedida), PagoVista::de);
	}

	/**
	 * Cómo va el cobro de una orden que ya se sabe del cliente, para su ticket.
	 */
	@Transactional(readOnly = true)
	public CobroDeOrden cobroDe(DetalleDelServicio detalle) {
		Optional<PagoApp> pagado = pagos.findFirstByOrdenIdAndEstado(detalle.id(), EstadoPago.PAGADO);
		if (pagado.isPresent()) {
			return new CobroDeOrden(PagoVista.de(pagado.get()), false);
		}
		return new CobroDeOrden(null, porQueNoSePaga(detalle) == null);
	}

	/** Mis tarjetas guardadas, la predeterminada primero (V2-M08.04). */
	@Transactional(readOnly = true)
	public List<MetodoPagoVista> metodos(Long clienteId) {
		YearMonth hoy = hoy();
		return metodos.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId).stream()
				.map(metodo -> MetodoPagoVista.de(metodo, hoy)).toList();
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si la tarjeta no existe o es de otro cliente (404)
	 */
	@Transactional
	public MetodoPagoVista predeterminar(Long clienteId, Long metodoPagoId) {
		pagos.bloquear(PREFIJO_TARJETAS | clienteId);
		MetodoPago elegido = tarjetaDelCliente(clienteId, metodoPagoId);
		if (!elegido.isPredeterminado()) {
			for (MetodoPago otro : metodos.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId)) {
				if (otro.isPredeterminado()) {
					otro.marcarPredeterminado(false);
					metodos.saveAndFlush(otro);
				}
			}
			elegido.marcarPredeterminado(true);
			metodos.saveAndFlush(elegido);
		}
		return MetodoPagoVista.de(elegido, hoy());
	}

	/**
	 * Borra la tarjeta guardada; si era la predeterminada, lo es la más vieja de
	 * las que quedan. Los pagos hechos con ella se conservan.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si la tarjeta no existe o es de otro cliente (404)
	 */
	@Transactional
	public void borrarMetodo(Long clienteId, Long metodoPagoId) {
		pagos.bloquear(PREFIJO_TARJETAS | clienteId);
		MetodoPago tarjeta = tarjetaDelCliente(clienteId, metodoPagoId);
		boolean eraPredeterminada = tarjeta.isPredeterminado();
		metodos.delete(tarjeta);
		metodos.flush();
		if (eraPredeterminada) {
			metodos.findByClienteIdOrderByPredeterminadoDescIdAsc(clienteId).stream().findFirst()
					.ifPresent(siguiente -> {
						siguiente.marcarPredeterminado(true);
						metodos.save(siguiente);
					});
		}
	}

	/**
	 * El personal cobró la orden en el mostrador. Si había un intento esperando el
	 * 3-D Secure, queda {@code FALLIDO} con {@code PAGADA_EN_CAJA}.
	 *
	 * @param referencia
	 *            el folio de caja; {@code null} para usar el del intento
	 * @throws RecursoNoEncontradoException
	 *             si la orden no existe (404)
	 * @throws ConflictoException
	 *             con {@code ORDEN_NO_PAGABLE} si aún no está lista, no tiene total
	 *             o ya está pagada (409)
	 */
	@Transactional
	public PagoVista pagarEnCaja(Long ordenId, Long usuarioId, String referencia) {
		String folio = referencia == null || referencia.isBlank() ? null : referencia.strip();
		if (folio != null && folio.length() > REFERENCIA_MAXIMA) {
			throw new IllegalArgumentException("La referencia admite hasta " + REFERENCIA_MAXIMA + " caracteres");
		}
		pagos.bloquear(PREFIJO_ORDEN | ordenId);
		DetalleDelServicio detalle = historial.detalle(ordenId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la orden " + ordenId));
		Long clienteId = vehiculos.buscarPorId(detalle.vehiculoId()).map(vehiculo -> vehiculo.propietario().id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la orden " + ordenId));
		if (pagos.findFirstByOrdenIdAndEstado(ordenId, EstadoPago.PAGADO).isPresent()) {
			throw new ConflictoException("La orden ya está pagada", CodigosError.ORDEN_NO_PAGABLE);
		}
		if (!detalle.estatus().cerrada() || detalle.total().signum() <= 0) {
			throw new ConflictoException("La orden aún no está lista para cobrarse", CodigosError.ORDEN_NO_PAGABLE);
		}
		Instant ahora = reloj.instant();
		Cobrable orden = new Cobrable(ordenId, detalle.vehiculoId(), clienteId, detalle.total());
		cerrarPendiente(orden, ahora, MotivoFalloPago.PAGADA_EN_CAJA);
		PagoApp pago = PagoApp.enCaja(orden, "caja_" + procesador.nuevaIntencion(), folio, usuarioId, ahora);
		pagos.saveAndFlush(pago);
		confirmar(pago);
		return PagoVista.de(pago);
	}

	private void aplicar(PagoApp pago, TarjetaDePrueba tarjeta, Resultado resultado, Instant ahora) {
		switch (resultado.respuesta()) {
			case APROBADO -> {
				pago.aprobar(ahora);
				pagos.saveAndFlush(pago);
				guardarTarjeta(pago, tarjeta, ahora);
				confirmar(pago);
			}
			case RECHAZADO -> pago.fallar(resultado.motivo(), ahora);
			case PIDE_AUTENTICACION -> pago.pedirAutenticacion(ahora);
			default -> throw new IllegalStateException("Respuesta desconocida " + resultado.respuesta());
		}
	}

	private void guardarTarjeta(PagoApp pago, TarjetaDePrueba tarjeta, Instant ahora) {
		if (!pago.isGuardarTarjeta()) {
			return;
		}
		Long clienteId = pago.getClienteId();
		pagos.bloquear(PREFIJO_TARJETAS | clienteId);
		Optional<MetodoPago> existente = metodos.findByClienteIdAndTokenProcesador(clienteId, tarjeta.token());
		if (existente.isPresent()) {
			existente.get().renovar(pago.getVence());
			pago.ligarTarjeta(existente.get().getId(), ahora);
			return;
		}
		long guardadas = metodos.countByClienteId(clienteId);
		if (guardadas >= TARJETAS_MAXIMAS) {
			return;
		}
		MetodoPago nueva = new MetodoPago(clienteId, procesador.nombre(), procesador.clienteDelProcesador(clienteId),
				tarjeta, pago.getVence(), ahora);
		nueva.marcarPredeterminado(guardadas == 0);
		metodos.saveAndFlush(nueva);
		pago.ligarTarjeta(nueva.getId(), ahora);
	}

	private void confirmar(PagoApp pago) {
		if (pago.getPedidoId() != null) {
			pedidos.bloquear(pago.getPedidoId()).orElseThrow().marcarPagado(pago.getPagadoEn());
		}
		eventos.publishEvent(new PagoConfirmado(pago.getId(), pago.getOrdenId(), pago.getClienteId(), pago.getMonto(),
				pago.getPedidoId()));
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("pagoId", pago.getId());
		// El aviso no admite nulos: va la orden o el pedido.
		if (pago.getOrdenId() != null) {
			datos.put("ordenId", pago.getOrdenId());
		} else {
			datos.put("pedidoId", pago.getPedidoId());
		}
		datos.put("estado", pago.getEstado().name());
		eventos.publishEvent(new AvisoAlCliente(pago.getClienteId(), EVENTO, datos));
	}

	/**
	 * Un intento que espera el 3-D Secure bloquea la orden o el pedido. Si ya
	 * venció se cierra, y si no, solo lo cierra la caja.
	 */
	private void cerrarPendiente(Cobrable objeto, Instant ahora, MotivoFalloPago motivoDeCaja) {
		Optional<PagoApp> pendiente = objeto.ordenId() != null
				? pagos.findFirstByOrdenIdAndEstado(objeto.ordenId(), EstadoPago.PENDIENTE)
				: pagos.findFirstByPedidoIdAndEstado(objeto.pedidoId(), EstadoPago.PENDIENTE);
		if (pendiente.isEmpty()) {
			return;
		}
		PagoApp abierto = pendiente.get();
		if (abierto.vencido(ahora, VIGENCIA_AUTENTICACION)) {
			abierto.fallar(MotivoFalloPago.AUTENTICACION_VENCIDA, ahora);
		} else if (motivoDeCaja != null) {
			abierto.fallar(motivoDeCaja, ahora);
		} else {
			String de = objeto.ordenId() != null ? "esta orden" : "este pedido";
			throw new ConflictoException("Ya hay un pago de " + de + " esperando la confirmación de tu banco",
					CodigosError.PAGO_EN_CURSO);
		}
		pagos.saveAndFlush(abierto);
	}

	private void vencerPendientes(Long clienteId) {
		Instant ahora = reloj.instant();
		for (PagoApp pendiente : pagos.findByClienteIdAndEstado(clienteId, EstadoPago.PENDIENTE)) {
			if (pendiente.vencido(ahora, VIGENCIA_AUTENTICACION)) {
				pendiente.fallar(MotivoFalloPago.AUTENTICACION_VENCIDA, ahora);
			}
		}
	}

	private Cobrable cobrable(Long clienteId, Long ordenId) {
		DetalleDelServicio detalle = historial.detalle(ordenId).filter(d -> esDelCliente(clienteId, d.vehiculoId()))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la orden " + ordenId));
		if (pagos.findFirstByOrdenIdAndEstado(ordenId, EstadoPago.PAGADO).isPresent()) {
			throw new ConflictoException("La orden ya está pagada", CodigosError.ORDEN_NO_PAGABLE);
		}
		String motivo = porQueNoSePaga(detalle);
		if (motivo != null) {
			throw new ConflictoException(motivo, CodigosError.ORDEN_NO_PAGABLE);
		}
		return new Cobrable(ordenId, detalle.vehiculoId(), clienteId, detalle.total());
	}

	/**
	 * {@code null} si la orden se puede pagar en la app, sin contar si ya se pagó.
	 */
	private String porQueNoSePaga(DetalleDelServicio detalle) {
		if (!detalle.estatus().cerrada()) {
			return "La orden aún no está lista para entrega";
		}
		if (detalle.total().compareTo(BigDecimal.ZERO) <= 0) {
			return "La orden no tiene nada que cobrar";
		}
		boolean delTaller = taller.orden(detalle.id()).map(orden -> !orden.etapas().isEmpty()).orElse(false);
		if (!delTaller) {
			return "Este servicio se registró ya hecho y se paga en la agencia";
		}
		return null;
	}

	private boolean esDelCliente(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(vehiculo -> vehiculo.propietario().id().equals(clienteId))
				.isPresent();
	}

	private Long perfilFiscal(Long clienteId, Long perfilFiscalId) {
		if (perfilFiscalId == null) {
			return null;
		}
		return perfiles.findByIdAndClienteId(perfilFiscalId, clienteId).map(PerfilFiscal::getId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el perfil fiscal " + perfilFiscalId));
	}

	private MetodoPago tarjetaDelCliente(Long clienteId, Long metodoPagoId) {
		return metodos.findByIdAndClienteId(metodoPagoId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la tarjeta " + metodoPagoId));
	}

	private YearMonth vencimiento(Integer mes, Integer anio) {
		if (mes == null || anio == null) {
			throw new IllegalArgumentException("Falta el vencimiento de la tarjeta");
		}
		if (mes < 1 || mes > 12) {
			throw new IllegalArgumentException("El mes de vencimiento va de 1 a 12");
		}
		YearMonth hoy = hoy();
		YearMonth vence;
		try {
			vence = YearMonth.of(anio, mes);
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("El año de vencimiento no es válido");
		}
		if (vence.isBefore(hoy)) {
			throw new IllegalArgumentException("La tarjeta ya venció");
		}
		if (vence.isAfter(hoy.plusYears(ANIOS_DE_VIGENCIA_MAXIMA))) {
			throw new IllegalArgumentException("El año de vencimiento no es válido");
		}
		return vence;
	}

	private YearMonth hoy() {
		return YearMonth.from(reloj.instant().atZone(zona));
	}

	private static void exigirUnMedio(NuevoPago nuevo) {
		if (nuevo == null) {
			throw new IllegalArgumentException("Falta con qué pagar");
		}
		boolean conToken = nuevo.token() != null && !nuevo.token().isBlank();
		int medios = (conToken ? 1 : 0) + (nuevo.metodoPagoId() != null ? 1 : 0) + (nuevo.billetera() != null ? 1 : 0);
		if (medios != 1) {
			throw new IllegalArgumentException(
					"Paga con una tarjeta nueva, una guardada o una billetera: solo una de las tres");
		}
		if (nuevo.billetera() != null && nuevo.billetera() != MedioPago.APPLE_PAY
				&& nuevo.billetera() != MedioPago.GOOGLE_PAY) {
			throw new IllegalArgumentException("Las billeteras son Apple Pay y Google Pay");
		}
	}

	private static String llave(String llave) {
		String limpia = llave == null ? "" : llave.strip();
		if (limpia.length() < LLAVE_MINIMA || limpia.length() > LLAVE_MAXIMA) {
			throw new IllegalArgumentException("La llave Idempotency-Key debe tener entre 8 y 64 caracteres");
		}
		return limpia;
	}
}
