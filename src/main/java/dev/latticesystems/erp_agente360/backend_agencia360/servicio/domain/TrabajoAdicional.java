package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EstadoAdicional;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Trabajo que el taller propone además de lo acordado (V2-M04.05). Los montos
 * traen el IVA incluido y el total es la mano de obra más las refacciones. El
 * cliente lo aprueba o lo rechaza una sola vez y con su firma; la fila guarda
 * quién respondió, cuándo y la clave de la firma, y ya no cambia.
 */
@Entity
@Table(name = "trabajo_adicional")
public class TrabajoAdicional extends Auditable {

	static final int CONCEPTO_MAXIMO = 200;
	static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999.99");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "orden_servicio_id", nullable = false)
	private Long ordenServicioId;

	@Column(nullable = false, length = CONCEPTO_MAXIMO)
	private String concepto;

	@Column(name = "mano_de_obra", nullable = false, precision = 12, scale = 2)
	private BigDecimal manoDeObra;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal refacciones;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoAdicional estado;

	@Column(name = "respondido_en")
	private Instant respondidoEn;

	@Column(name = "respondido_por")
	private Long respondidoPor;

	@Column(name = "firma_url", length = 300)
	private String firmaUrl;

	protected TrabajoAdicional() {
	}

	private TrabajoAdicional(Long ordenServicioId, String concepto, BigDecimal manoDeObra, BigDecimal refacciones) {
		this.ordenServicioId = ordenServicioId;
		this.concepto = concepto;
		this.manoDeObra = manoDeObra;
		this.refacciones = refacciones;
		this.total = manoDeObra.add(refacciones);
		this.estado = EstadoAdicional.PENDIENTE;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el concepto viene vacío o pasa de 200 caracteres, un monto es
	 *             negativo o tiene más de dos decimales, o el total es cero
	 */
	public static TrabajoAdicional proponer(Long ordenServicioId, String concepto, BigDecimal manoDeObra,
			BigDecimal refacciones) {
		Objects.requireNonNull(ordenServicioId, "ordenServicioId");
		String texto = concepto == null ? "" : concepto.strip();
		if (texto.isEmpty() || texto.length() > CONCEPTO_MAXIMO) {
			throw new IllegalArgumentException(
					"El concepto es obligatorio y admite hasta " + CONCEPTO_MAXIMO + " caracteres");
		}
		BigDecimal mano = monto(manoDeObra, "mano de obra");
		BigDecimal partes = monto(refacciones, "refacciones");
		BigDecimal total = mano.add(partes);
		if (total.signum() == 0) {
			throw new IllegalArgumentException("El trabajo adicional debe costar algo");
		}
		if (total.compareTo(MONTO_MAXIMO) > 0) {
			throw new IllegalArgumentException("El total del trabajo no puede pasar de " + MONTO_MAXIMO);
		}
		return new TrabajoAdicional(ordenServicioId, texto, mano, partes);
	}

	private static BigDecimal monto(BigDecimal valor, String nombre) {
		if (valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException(
					"El monto de " + nombre + " debe ser cero o positivo, con dos decimales");
		}
		return valor.setScale(2);
	}

	/**
	 * Registra la respuesta del cliente con la clave de su firma.
	 *
	 * @throws IllegalStateException
	 *             si ya se respondió
	 */
	public void responder(boolean aprobado, Long clienteId, String claveFirma, Instant momento) {
		Objects.requireNonNull(clienteId, "clienteId");
		Objects.requireNonNull(claveFirma, "claveFirma");
		Objects.requireNonNull(momento, "momento");
		if (!estaPendiente()) {
			throw new IllegalStateException("El trabajo adicional ya se respondió");
		}
		estado = aprobado ? EstadoAdicional.APROBADO : EstadoAdicional.RECHAZADO;
		respondidoPor = clienteId;
		firmaUrl = claveFirma;
		respondidoEn = momento;
	}

	public boolean estaPendiente() {
		return estado == EstadoAdicional.PENDIENTE;
	}

	public Long getId() {
		return id;
	}

	public Long getOrdenServicioId() {
		return ordenServicioId;
	}

	public String getConcepto() {
		return concepto;
	}

	public BigDecimal getManoDeObra() {
		return manoDeObra;
	}

	public BigDecimal getRefacciones() {
		return refacciones;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public EstadoAdicional getEstado() {
		return estado;
	}

	public Instant getRespondidoEn() {
		return respondidoEn;
	}

	public Long getRespondidoPor() {
		return respondidoPor;
	}

	public String getFirmaUrl() {
		return firmaUrl;
	}
}
