package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Un intento de pagar una orden de servicio (V2-M08.02, tabla {@code pago_app}
 * de la ficha) o un pedido de la tienda (V2-M14.04). Nace {@code PENDIENTE} con
 * su llave de idempotencia y queda {@code PAGADO} o {@code FALLIDO}. Ninguno se
 * borra. El de caja nace pagado. Los ids de otros módulos (orden, vehículo,
 * cliente, perfil fiscal y usuario) solo se mapean como ids para no cruzar
 * módulos.
 */
@Entity
@Table(name = "pago_app")
public class PagoApp {

	public static final String MONEDA = "MXN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "orden_servicio_id", updatable = false)
	private Long ordenId;

	@Column(name = "pedido_app_id", updatable = false)
	private Long pedidoId;

	@Column(name = "vehiculo_cliente_id", updatable = false)
	private Long vehiculoId;

	@Column(name = "cliente_id", nullable = false, updatable = false)
	private Long clienteId;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, updatable = false, length = 3)
	private String moneda;

	@Column(nullable = false, updatable = false, length = 20)
	private String procesador;

	@Column(name = "intencion_id", nullable = false, updatable = false, length = 64)
	private String intencionId;

	@Column(name = "llave_idempotencia", updatable = false, length = 64)
	private String llaveIdempotencia;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo", nullable = false, updatable = false, length = 15)
	private MedioPago medio;

	@Column(updatable = false, length = 20)
	private String marca;

	@Column(updatable = false, length = 4)
	private String ultimos4;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_tarjeta", updatable = false, length = 10)
	private TipoTarjeta tipoTarjeta;

	@Column(name = "token_procesador", length = 64)
	private String tokenProcesador;

	@Column(name = "mes_vence", updatable = false)
	private Short mesVence;

	@Column(name = "anio_vence", updatable = false)
	private Short anioVence;

	@Column(name = "guardar_tarjeta", nullable = false, updatable = false)
	private boolean guardarTarjeta;

	@Column(name = "metodo_pago_id")
	private Long metodoPagoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoPago estado;

	@Enumerated(EnumType.STRING)
	@Column(length = 12)
	private AutenticacionPago autenticacion;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_fallo", length = 30)
	private MotivoFalloPago motivoFallo;

	@Column(name = "perfil_fiscal_id")
	private Long perfilFiscalId;

	@Column(name = "referencia_caja", updatable = false, length = 40)
	private String referenciaCaja;

	@Column(name = "registrado_por", updatable = false)
	private Long registradoPor;

	@Column(name = "pagado_en")
	private Instant pagadoEn;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@OneToOne(mappedBy = "pago", fetch = FetchType.LAZY)
	private FacturaApp factura;

	protected PagoApp() {
		// Requerido por JPA
	}

	private PagoApp(Cobrable orden, String procesador, String intencionId, MedioPago medio, Instant ahora) {
		this.ordenId = orden.ordenId();
		this.pedidoId = orden.pedidoId();
		this.vehiculoId = orden.vehiculoId();
		this.clienteId = Objects.requireNonNull(orden.clienteId(), "clienteId");
		this.monto = Objects.requireNonNull(orden.monto(), "monto");
		this.moneda = MONEDA;
		this.procesador = Objects.requireNonNull(procesador, "procesador");
		this.intencionId = Objects.requireNonNull(intencionId, "intencionId");
		this.medio = medio;
		this.creadoEn = Objects.requireNonNull(ahora, "ahora");
		this.actualizadoEn = ahora;
	}

	/**
	 * Un intento con tarjeta, todavía sin cobrar.
	 *
	 * @param vence
	 *            el vencimiento de la tarjeta
	 * @param metodoPagoId
	 *            la tarjeta guardada con que se paga; {@code null} si es nueva
	 * @param guardarTarjeta
	 *            si la tarjeta nueva se guarda al quedar pagado
	 */
	public static PagoApp conTarjeta(Cobrable orden, String procesador, String intencionId, String llave,
			TarjetaDePrueba tarjeta, YearMonth vence, Long metodoPagoId, boolean guardarTarjeta, Long perfilFiscalId,
			Instant ahora) {
		PagoApp pago = new PagoApp(orden, procesador, intencionId, MedioPago.TARJETA, ahora);
		pago.llaveIdempotencia = Objects.requireNonNull(llave, "llave");
		pago.marca = tarjeta.marca();
		pago.ultimos4 = tarjeta.ultimos4();
		pago.tipoTarjeta = tarjeta.tipo();
		pago.tokenProcesador = tarjeta.token();
		pago.mesVence = (short) vence.getMonthValue();
		pago.anioVence = (short) vence.getYear();
		pago.metodoPagoId = metodoPagoId;
		pago.guardarTarjeta = metodoPagoId == null && guardarTarjeta;
		pago.perfilFiscalId = perfilFiscalId;
		pago.estado = EstadoPago.PENDIENTE;
		return pago;
	}

	/**
	 * Un intento con una billetera simulada (V2-M08.03): sin marca, últimos 4 ni
	 * vencimiento, como crédito para la forma de pago 04, y nunca se guarda como
	 * tarjeta.
	 *
	 * @param billetera
	 *            la tarjeta de prueba de la billetera, de
	 *            {@link TarjetaDePrueba#deBilletera(MedioPago)}
	 */
	public static PagoApp conBilletera(Cobrable orden, String procesador, String intencionId, String llave,
			MedioPago medio, TarjetaDePrueba billetera, Long perfilFiscalId, Instant ahora) {
		if (medio != MedioPago.APPLE_PAY && medio != MedioPago.GOOGLE_PAY) {
			throw new IllegalArgumentException("Las billeteras son Apple Pay y Google Pay");
		}
		PagoApp pago = new PagoApp(orden, procesador, intencionId, medio, ahora);
		pago.llaveIdempotencia = Objects.requireNonNull(llave, "llave");
		pago.tipoTarjeta = billetera.tipo();
		pago.tokenProcesador = billetera.token();
		pago.perfilFiscalId = perfilFiscalId;
		pago.estado = EstadoPago.PENDIENTE;
		return pago;
	}

	/** La orden que el personal cobró en el mostrador: nace pagada. */
	public static PagoApp enCaja(Cobrable orden, String intencionId, String referencia, Long registradoPor,
			Instant ahora) {
		PagoApp pago = new PagoApp(orden, "CAJA", intencionId, MedioPago.CAJA, ahora);
		pago.referenciaCaja = referencia;
		pago.registradoPor = Objects.requireNonNull(registradoPor, "registradoPor");
		pago.estado = EstadoPago.PAGADO;
		pago.pagadoEn = ahora;
		return pago;
	}

	/** El banco simulado pide confirmar con 3-D Secure; el pago sigue pendiente. */
	public void pedirAutenticacion(Instant ahora) {
		exigirPendiente();
		autenticacion = AutenticacionPago.REQUERIDA;
		actualizadoEn = ahora;
	}

	public void aprobar(Instant ahora) {
		exigirPendiente();
		estado = EstadoPago.PAGADO;
		pagadoEn = ahora;
		if (autenticacion == AutenticacionPago.REQUERIDA) {
			autenticacion = AutenticacionPago.APROBADA;
		}
		actualizadoEn = ahora;
	}

	public void fallar(MotivoFalloPago motivo, Instant ahora) {
		exigirPendiente();
		estado = EstadoPago.FALLIDO;
		motivoFallo = Objects.requireNonNull(motivo, "motivo");
		if (autenticacion == AutenticacionPago.REQUERIDA) {
			autenticacion = AutenticacionPago.RECHAZADA;
		}
		actualizadoEn = ahora;
	}

	/**
	 * Los datos fiscales con que se facturó el pago, si al pagar no se eligieron
	 * (V2-M08.06).
	 */
	public void elegirPerfilFiscal(Long perfilFiscalId, Instant ahora) {
		this.perfilFiscalId = Objects.requireNonNull(perfilFiscalId, "perfilFiscalId");
		this.actualizadoEn = ahora;
	}

	/**
	 * Se puede facturar desde la app: está pagado, no se pagó en caja (se factura
	 * en la agencia) y aún no tiene factura.
	 */
	public boolean facturable() {
		return estado == EstadoPago.PAGADO && medio != MedioPago.CAJA && factura == null;
	}

	/** La factura timbrada del pago; {@code null} si aún no tiene. */
	public FacturaApp getFactura() {
		return factura;
	}

	void ligarFactura(FacturaApp factura) {
		this.factura = factura;
	}

	/** La tarjeta con que se pagó quedó guardada. */
	public void ligarTarjeta(Long metodoPagoId, Instant ahora) {
		this.metodoPagoId = metodoPagoId;
		this.actualizadoEn = ahora;
	}

	/**
	 * Pendiente desde hace más de {@code vigencia}: nadie respondió el 3-D Secure.
	 */
	public boolean vencido(Instant ahora, Duration vigencia) {
		return estado == EstadoPago.PENDIENTE && !creadoEn.plus(vigencia).isAfter(ahora);
	}

	public boolean requiereAutenticacion() {
		return estado == EstadoPago.PENDIENTE && autenticacion == AutenticacionPago.REQUERIDA;
	}

	/**
	 * Lo que la app muestra como referencia: el folio de caja o el del procesador.
	 */
	public String getReferencia() {
		return referenciaCaja != null ? referenciaCaja : intencionId;
	}

	private void exigirPendiente() {
		if (estado != EstadoPago.PENDIENTE) {
			throw new IllegalStateException("El pago " + id + " ya no está pendiente");
		}
	}

	public Long getId() {
		return id;
	}

	/** {@code null} si es el pago de un pedido. */
	public Long getOrdenId() {
		return ordenId;
	}

	/** {@code null} si es el pago de una orden (V2-M14.04). */
	public Long getPedidoId() {
		return pedidoId;
	}

	public Long getVehiculoId() {
		return vehiculoId;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public String getMoneda() {
		return moneda;
	}

	public String getProcesador() {
		return procesador;
	}

	public String getIntencionId() {
		return intencionId;
	}

	public String getLlaveIdempotencia() {
		return llaveIdempotencia;
	}

	public MedioPago getMedio() {
		return medio;
	}

	public String getMarca() {
		return marca;
	}

	public String getUltimos4() {
		return ultimos4;
	}

	public TipoTarjeta getTipoTarjeta() {
		return tipoTarjeta;
	}

	public String getTokenProcesador() {
		return tokenProcesador;
	}

	/** El vencimiento de la tarjeta; {@code null} en un pago de caja. */
	public YearMonth getVence() {
		return mesVence == null ? null : YearMonth.of(anioVence, mesVence);
	}

	public boolean isGuardarTarjeta() {
		return guardarTarjeta;
	}

	public Long getMetodoPagoId() {
		return metodoPagoId;
	}

	public EstadoPago getEstado() {
		return estado;
	}

	public AutenticacionPago getAutenticacion() {
		return autenticacion;
	}

	public MotivoFalloPago getMotivoFallo() {
		return motivoFallo;
	}

	public Long getPerfilFiscalId() {
		return perfilFiscalId;
	}

	public Long getRegistradoPor() {
		return registradoPor;
	}

	public Instant getPagadoEn() {
		return pagadoEn;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	/**
	 * Lo que se cobra de una orden o de un pedido: de quién es y cuánto, con IVA
	 * incluido. Lleva la orden con su vehículo o el pedido, nunca los dos.
	 */
	public record Cobrable(Long ordenId, Long vehiculoId, Long clienteId, BigDecimal monto, Long pedidoId) {

		public Cobrable {
			if ((ordenId == null) == (pedidoId == null)) {
				throw new IllegalArgumentException("Se cobra una orden o un pedido, no los dos");
			}
			if (ordenId != null) {
				Objects.requireNonNull(vehiculoId, "vehiculoId");
			}
			Objects.requireNonNull(clienteId, "clienteId");
			Objects.requireNonNull(monto, "monto");
		}

		/** Una orden de servicio, con el vehículo que se atendió. */
		public Cobrable(Long ordenId, Long vehiculoId, Long clienteId, BigDecimal monto) {
			this(ordenId, vehiculoId, clienteId, monto, null);
		}

		/** Un pedido de la tienda (V2-M14.04), sin vehículo. */
		public static Cobrable dePedido(Long pedidoId, Long clienteId, BigDecimal monto) {
			return new Cobrable(null, null, clienteId, monto, pedidoId);
		}
	}
}
