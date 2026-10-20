package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Verificación de los pagos de la venta de un prospecto (ERP-1.1, decisión del
 * PO del Sprint 2): Caja solo registra y envía los pagos; Contabilidad valida
 * que correspondan y cubran la venta. Al validarlos, el prospecto pasa de Pago
 * a Facturación desde el ERP.
 *
 * <p>
 * Hay una sola por prospecto. Se liga al prospecto porque la venta se registra
 * hasta que el prospecto llega a Finalizado (ERP-1.0) y ya no existe el
 * apartado de la unidad.
 *
 * <p>
 * Pendiente del PO: contra qué precio se valida que los pagos cubran la venta.
 * Mientras tanto Contabilidad ve el total pagado y decide.
 */
@Entity
@Table(name = "liquidacion")
public class Liquidacion extends Auditable {

	private static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999.99");
	private static final int LARGO_MAXIMO_REFERENCIA = 60;
	private static final int LARGO_MAXIMO_MOTIVO = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prospecto_id", nullable = false, unique = true)
	private Prospecto prospecto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoLiquidacion estado;

	@OneToMany(mappedBy = "liquidacion", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("fechaPago asc, id asc")
	private List<Pago> pagos = new ArrayList<>();

	@Column(name = "enviada_por_id")
	private Long enviadaPorId;

	@Column(name = "enviada_en")
	private Instant enviadaEn;

	@Column(name = "resuelta_por_id")
	private Long resueltaPorId;

	@Column(name = "resuelta_en")
	private Instant resueltaEn;

	/** Por qué Contabilidad regresó los pagos; solo si está rechazada. */
	@Column(name = "motivo_rechazo", length = 200)
	private String motivoRechazo;

	protected Liquidacion() {
		// Requerido por JPA
	}

	private Liquidacion(Prospecto prospecto) {
		this.prospecto = prospecto;
		this.estado = EstadoLiquidacion.ABIERTA;
	}

	/**
	 * @throws ConflictoException
	 *             si el prospecto no está en Pago
	 */
	public static Liquidacion abrir(Prospecto prospecto) {
		Objects.requireNonNull(prospecto, "prospecto");
		exigirEnPago(prospecto);
		return new Liquidacion(prospecto);
	}

	/**
	 * Caja registra un pago. Si Contabilidad había rechazado los pagos, la
	 * liquidación se vuelve a abrir para corregirla.
	 *
	 * @throws IllegalArgumentException
	 *             si el monto no es mayor a cero, tiene más de dos decimales o es
	 *             demasiado grande, o si la fecha es futura
	 * @throws ConflictoException
	 *             si los pagos ya se enviaron o el prospecto ya no está en Pago
	 */
	public Pago registrarPago(BigDecimal monto, LocalDate fechaPago, String referencia, Long cajeroId, LocalDate hoy) {
		Objects.requireNonNull(cajeroId, "cajeroId");
		exigirEditable();
		BigDecimal montoValido = validarMonto(monto);
		if (fechaPago == null) {
			throw new IllegalArgumentException("La fecha del pago es obligatoria");
		}
		if (fechaPago.isAfter(hoy)) {
			throw new IllegalArgumentException("La fecha del pago no puede ser futura");
		}
		String referenciaLimpia = limpiar(referencia);
		if (referenciaLimpia != null && referenciaLimpia.length() > LARGO_MAXIMO_REFERENCIA) {
			throw new IllegalArgumentException("La referencia admite hasta 60 caracteres");
		}
		Pago pago = new Pago(this, montoValido, fechaPago, referenciaLimpia, cajeroId);
		pagos.add(pago);
		reabrirSiFueRechazada();
		return pago;
	}

	/**
	 * Caja anula un pago mal capturado antes de enviarlos. No se borra: queda
	 * registrado y deja de contar en el total.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el pago no es de esta liquidación
	 * @throws ConflictoException
	 *             si los pagos ya se enviaron o ese pago ya estaba anulado
	 */
	public void anularPago(Long pagoId, Long cajeroId, Instant ahora) {
		exigirEditable();
		Pago pago = pagos.stream().filter(p -> Objects.equals(p.getId(), pagoId)).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + pagoId));
		pago.anular(cajeroId, ahora);
		reabrirSiFueRechazada();
	}

	/**
	 * Caja envía los pagos a Contabilidad.
	 *
	 * @throws ConflictoException
	 *             si no hay pagos, ya se enviaron o el prospecto ya no está en Pago
	 */
	public void enviar(Long cajeroId, Instant ahora) {
		exigirEditable();
		if (pagos.stream().allMatch(Pago::isAnulado)) {
			throw new ConflictoException("Registra al menos un pago antes de enviarlos a Contabilidad");
		}
		estado = EstadoLiquidacion.ENVIADA;
		enviadaPorId = Objects.requireNonNull(cajeroId, "cajeroId");
		enviadaEn = Objects.requireNonNull(ahora, "ahora");
		motivoRechazo = null;
		resueltaPorId = null;
		resueltaEn = null;
	}

	/**
	 * Contabilidad aprueba los pagos y el prospecto pasa de Pago a Facturación.
	 *
	 * @throws ConflictoException
	 *             si Caja no los ha enviado o el prospecto ya no está en Pago
	 */
	public void validar(Long contadorId, Instant ahora) {
		exigirEnviada();
		prospecto.validarPago(contadorId, ahora);
		resolver(EstadoLiquidacion.VALIDADA, contadorId, ahora);
	}

	/**
	 * Contabilidad regresa los pagos a Caja con el motivo. El prospecto sigue en
	 * Pago.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el motivo o pasa de 200 caracteres
	 * @throws ConflictoException
	 *             si Caja no los ha enviado
	 */
	public void rechazar(String motivo, Long contadorId, Instant ahora) {
		exigirEnviada();
		String motivoLimpio = limpiar(motivo);
		if (motivoLimpio == null) {
			throw new IllegalArgumentException("Escribe por qué se rechazan los pagos");
		}
		if (motivoLimpio.length() > LARGO_MAXIMO_MOTIVO) {
			throw new IllegalArgumentException("El motivo admite hasta 200 caracteres");
		}
		resolver(EstadoLiquidacion.RECHAZADA, contadorId, ahora);
		motivoRechazo = motivoLimpio;
	}

	/** Suma de los pagos registrados, sin los anulados. */
	public BigDecimal total() {
		return pagos.stream().filter(p -> !p.isAnulado()).map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private void resolver(EstadoLiquidacion nuevo, Long contadorId, Instant ahora) {
		estado = nuevo;
		resueltaPorId = Objects.requireNonNull(contadorId, "contadorId");
		resueltaEn = Objects.requireNonNull(ahora, "ahora");
	}

	private void reabrirSiFueRechazada() {
		if (estado == EstadoLiquidacion.RECHAZADA) {
			estado = EstadoLiquidacion.ABIERTA;
		}
	}

	private void exigirEditable() {
		if (estado == EstadoLiquidacion.ENVIADA || estado == EstadoLiquidacion.VALIDADA) {
			throw new ConflictoException("Los pagos ya se enviaron a Contabilidad y no se pueden cambiar");
		}
		exigirEnPago(prospecto);
	}

	private void exigirEnviada() {
		if (estado != EstadoLiquidacion.ENVIADA) {
			throw new ConflictoException("Contabilidad solo revisa pagos que Caja ya envió");
		}
	}

	private static void exigirEnPago(Prospecto prospecto) {
		if (prospecto.getEtapa() != Etapa.PAGO) {
			throw new ConflictoException("Solo se registran pagos de un prospecto en Pago");
		}
	}

	private static BigDecimal validarMonto(BigDecimal monto) {
		if (monto == null || monto.signum() <= 0) {
			throw new IllegalArgumentException("El monto debe ser mayor a cero");
		}
		if (monto.stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException("El monto admite hasta dos decimales");
		}
		if (monto.compareTo(MONTO_MAXIMO) > 0) {
			throw new IllegalArgumentException("El monto es demasiado grande");
		}
		return monto.setScale(2);
	}

	private static String limpiar(String valor) {
		return valor == null || valor.isBlank() ? null : valor.strip();
	}

	public Long getId() {
		return id;
	}

	public Prospecto getProspecto() {
		return prospecto;
	}

	public EstadoLiquidacion getEstado() {
		return estado;
	}

	public List<Pago> getPagos() {
		return Collections.unmodifiableList(pagos);
	}

	public Long getEnviadaPorId() {
		return enviadaPorId;
	}

	public Instant getEnviadaEn() {
		return enviadaEn;
	}

	public Long getResueltaPorId() {
		return resueltaPorId;
	}

	public Instant getResueltaEn() {
		return resueltaEn;
	}

	public String getMotivoRechazo() {
		return motivoRechazo;
	}
}
