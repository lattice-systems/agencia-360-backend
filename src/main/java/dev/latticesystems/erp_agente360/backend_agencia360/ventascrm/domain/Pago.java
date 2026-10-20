package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Un pago que Caja registró para la venta de un prospecto (ERP-1.1). Se crea y
 * se anula solo a través de {@link Liquidacion}. La fecha y hora de registro es
 * {@code created_at}.
 *
 * <p>
 * El dinero no se borra: un pago mal capturado se anula y queda en el registro
 * con quién y cuándo, pero deja de contar en el total.
 */
@Entity
@Table(name = "pago")
public class Pago extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "liquidacion_id", nullable = false)
	private Liquidacion liquidacion;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	/** Día en que el cliente pagó; puede ser anterior al registro. */
	@Column(name = "fecha_pago", nullable = false)
	private LocalDate fechaPago;

	/** Folio de la transferencia, número de recibo u otra referencia. */
	@Column(length = 60)
	private String referencia;

	@Column(name = "registrado_por_id", nullable = false)
	private Long registradoPorId;

	@Column(nullable = false)
	private boolean anulado;

	@Column(name = "anulado_por_id")
	private Long anuladoPorId;

	@Column(name = "anulado_en")
	private Instant anuladoEn;

	protected Pago() {
		// Requerido por JPA
	}

	Pago(Liquidacion liquidacion, BigDecimal monto, LocalDate fechaPago, String referencia, Long registradoPorId) {
		this.liquidacion = liquidacion;
		this.monto = monto;
		this.fechaPago = fechaPago;
		this.referencia = referencia;
		this.registradoPorId = registradoPorId;
	}

	void anular(Long cajeroId, Instant ahora) {
		if (anulado) {
			throw new ConflictoException("Ese pago ya está anulado");
		}
		anulado = true;
		anuladoPorId = Objects.requireNonNull(cajeroId, "cajeroId");
		anuladoEn = Objects.requireNonNull(ahora, "ahora");
	}

	public Long getId() {
		return id;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public LocalDate getFechaPago() {
		return fechaPago;
	}

	public String getReferencia() {
		return referencia;
	}

	public Long getRegistradoPorId() {
		return registradoPorId;
	}

	public boolean isAnulado() {
		return anulado;
	}

	public Long getAnuladoPorId() {
		return anuladoPorId;
	}

	public Instant getAnuladoEn() {
		return anuladoEn;
	}
}
