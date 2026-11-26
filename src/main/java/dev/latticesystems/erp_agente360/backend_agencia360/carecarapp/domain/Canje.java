package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Una recompensa canjeada (V2-M12.03): un código que el cliente muestra en la
 * agencia como QR. Guarda los puntos que costó. Vigente hasta que el personal
 * lo usa o hasta su vencimiento; al vencer sin usarse, sus puntos regresan.
 */
@Entity
@Table(name = "canje")
public class Canje {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(name = "recompensa_id", nullable = false)
	private Long recompensaId;

	@Column(nullable = false, length = 8)
	private String codigo;

	@Column(nullable = false)
	private int puntos;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private EstadoCanje estado;

	@Column(name = "vence_en", nullable = false)
	private Instant venceEn;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	protected Canje() {
	}

	public Canje(Long clienteId, Recompensa recompensa, String codigo, Instant ahora, Instant venceEn) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.recompensaId = Objects.requireNonNull(recompensa.getId(), "recompensaId");
		this.puntos = recompensa.getPuntos();
		this.codigo = Objects.requireNonNull(codigo, "codigo");
		this.creadoEn = Objects.requireNonNull(ahora, "ahora");
		if (!venceEn.isAfter(ahora)) {
			throw new IllegalArgumentException("El canje debe vencer después de crearse");
		}
		this.venceEn = venceEn;
		this.estado = EstadoCanje.VIGENTE;
	}

	/** Vigente y sin pasar su vencimiento. */
	public boolean usable(Instant ahora) {
		return estado == EstadoCanje.VIGENTE && ahora.isBefore(venceEn);
	}

	/**
	 * El personal lo usó en la agencia.
	 *
	 * @throws IllegalStateException
	 *             si ya se usó, ya venció o pasó su vencimiento
	 */
	public void usar(Instant ahora) {
		if (!usable(ahora)) {
			throw new IllegalStateException("El canje " + codigo + " ya no está vigente");
		}
		estado = EstadoCanje.USADO;
		usadoEn = ahora;
	}

	/**
	 * Venció sin usarse; quien lo llama regresa los puntos.
	 *
	 * @throws IllegalStateException
	 *             si no está vigente o todavía no vence
	 */
	public void vencer(Instant ahora) {
		if (estado != EstadoCanje.VIGENTE || ahora.isBefore(venceEn)) {
			throw new IllegalStateException("El canje " + codigo + " no se puede vencer");
		}
		estado = EstadoCanje.VENCIDO;
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Long getRecompensaId() {
		return recompensaId;
	}

	public String getCodigo() {
		return codigo;
	}

	public int getPuntos() {
		return puntos;
	}

	public EstadoCanje getEstado() {
		return estado;
	}

	public Instant getVenceEn() {
		return venceEn;
	}

	public Instant getUsadoEn() {
		return usadoEn;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
