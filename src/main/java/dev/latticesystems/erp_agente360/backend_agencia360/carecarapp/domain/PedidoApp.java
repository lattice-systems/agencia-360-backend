package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * Pedido de la tienda de la app (V2-M14). Nace apartado, con las piezas ya
 * fuera de la existencia, y vence si no se recoge. Mientras está abierto
 * (apartado o listo) siempre tiene fecha de vencimiento.
 */
@Entity
@Table(name = "pedido_app")
public class PedidoApp extends Auditable {

	private static final Set<EstadoPedido> ABIERTOS = Set.of(EstadoPedido.APARTADO, EstadoPedido.LISTO);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoPedido estado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EntregaPedido entrega;

	@Column(name = "cita_id")
	private Long citaId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Column(name = "vence_en")
	private Instant venceEn;

	@Column(name = "llave_idempotencia", nullable = false, length = 64)
	private String llaveIdempotencia;

	@Column(name = "listo_en")
	private Instant listoEn;

	@Column(name = "entregado_en")
	private Instant entregadoEn;

	@Column(name = "cancelado_en")
	private Instant canceladoEn;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_cancelacion", length = 10)
	private MotivoCancelacionPedido motivoCancelacion;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	@BatchSize(size = 50)
	private List<PedidoRenglon> renglones = new ArrayList<>();

	protected PedidoApp() {
		// Requerido por JPA
	}

	/**
	 * Un pedido apartado, todavía sin renglones: se agregan con {@link #agregar}
	 * cuando el inventario confirma las piezas y su precio.
	 *
	 * @param citaId
	 *            nulo si se recoge en mostrador
	 */
	public static PedidoApp apartar(Long clienteId, String llaveIdempotencia, Long citaId, Instant venceEn) {
		PedidoApp pedido = new PedidoApp();
		pedido.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		pedido.llaveIdempotencia = Objects.requireNonNull(llaveIdempotencia, "llaveIdempotencia");
		pedido.estado = EstadoPedido.APARTADO;
		pedido.total = BigDecimal.ZERO;
		pedido.entregarEn(citaId, venceEn);
		return pedido;
	}

	public void agregar(Long refaccionId, int cantidad, BigDecimal precio) {
		PedidoRenglon renglon = new PedidoRenglon(this, refaccionId, cantidad, precio);
		renglones.add(renglon);
		total = total.add(renglon.getImporte());
	}

	/**
	 * Cambia dónde se entrega: en una cita o, con {@code citaId} nulo, en mostrador
	 * (V2-M14.03).
	 *
	 * @throws ConflictoException
	 *             con {@code PEDIDO_NO_MODIFICABLE} si ya no está abierto
	 */
	public void instalarEn(Long citaId, Instant venceEn) {
		if (!abierto()) {
			throw new ConflictoException("El pedido ya no se puede cambiar", CodigosError.PEDIDO_NO_MODIFICABLE);
		}
		entregarEn(citaId, venceEn);
	}

	private void entregarEn(Long citaId, Instant venceEn) {
		this.citaId = citaId;
		this.entrega = citaId == null ? EntregaPedido.MOSTRADOR : EntregaPedido.CITA;
		this.venceEn = Objects.requireNonNull(venceEn, "venceEn");
	}

	/**
	 * La agencia ya lo tiene preparado; quedan al menos {@code paraRecoger} para
	 * pasar por él.
	 *
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si no está apartado
	 */
	public void marcarListo(Instant ahora, Duration paraRecoger) {
		exigir(EstadoPedido.APARTADO);
		estado = EstadoPedido.LISTO;
		listoEn = ahora;
		Instant minimo = ahora.plus(paraRecoger);
		if (venceEn.isBefore(minimo)) {
			venceEn = minimo;
		}
	}

	/**
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si no está listo
	 */
	public void entregar(Instant ahora) {
		exigir(EstadoPedido.LISTO);
		estado = EstadoPedido.ENTREGADO;
		entregadoEn = ahora;
		venceEn = null;
	}

	/**
	 * Cancela un pedido abierto. Quien llama libera las piezas.
	 *
	 * @throws ConflictoException
	 *             con {@code PEDIDO_NO_CANCELABLE} si ya no está abierto
	 */
	public void cancelar(MotivoCancelacionPedido motivo, Instant ahora) {
		if (!abierto()) {
			throw new ConflictoException("El pedido ya no se puede cancelar", CodigosError.PEDIDO_NO_CANCELABLE);
		}
		estado = EstadoPedido.CANCELADO;
		motivoCancelacion = Objects.requireNonNull(motivo, "motivo");
		canceladoEn = ahora;
		venceEn = null;
	}

	private void exigir(EstadoPedido esperado) {
		if (estado != esperado) {
			throw new ConflictoException("El pedido no admite ese cambio de estado", CodigosError.TRANSICION_INVALIDA);
		}
	}

	public boolean abierto() {
		return ABIERTOS.contains(estado);
	}

	public boolean vencido(Instant ahora) {
		return abierto() && !venceEn.isAfter(ahora);
	}

	public static Set<EstadoPedido> estadosAbiertos() {
		return ABIERTOS;
	}

	/** Piezas por id de refacción, como las pide el inventario. */
	public Map<Long, Integer> cantidades() {
		Map<Long, Integer> cantidades = new LinkedHashMap<>();
		renglones.forEach(renglon -> cantidades.put(renglon.getRefaccionId(), renglon.getCantidad()));
		return cantidades;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public EstadoPedido getEstado() {
		return estado;
	}

	public EntregaPedido getEntrega() {
		return entrega;
	}

	public Long getCitaId() {
		return citaId;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public Instant getVenceEn() {
		return venceEn;
	}

	public Instant getListoEn() {
		return listoEn;
	}

	public Instant getEntregadoEn() {
		return entregadoEn;
	}

	public Instant getCanceladoEn() {
		return canceladoEn;
	}

	public MotivoCancelacionPedido getMotivoCancelacion() {
		return motivoCancelacion;
	}

	public List<PedidoRenglon> getRenglones() {
		return Collections.unmodifiableList(renglones);
	}
}
