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
 * Puntos que gana o gasta un cliente (V2-M12). El saldo es la suma de sus
 * movimientos; un movimiento no se borra ni se edita. La referencia dice de
 * dónde salió (una orden, un canje o un referido) y, con el motivo, es única.
 */
@Entity
@Table(name = "movimiento_puntos")
public class MovimientoPuntos {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(nullable = false)
	private int puntos;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MotivoPuntos motivo;

	@Column(name = "referencia_tipo", length = 20)
	private String referenciaTipo;

	@Column(name = "referencia_id")
	private Long referenciaId;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	protected MovimientoPuntos() {
	}

	/**
	 * @throws IllegalArgumentException
	 *             si los puntos son cero o el signo no corresponde al motivo
	 */
	public MovimientoPuntos(Long clienteId, int puntos, MotivoPuntos motivo, String referenciaTipo, Long referenciaId,
			Instant creadoEn) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.motivo = Objects.requireNonNull(motivo, "motivo");
		if (puntos == 0) {
			throw new IllegalArgumentException("Un movimiento de puntos no puede ser de cero");
		}
		if (motivo == MotivoPuntos.CANJE ? puntos > 0 : puntos < 0 && motivo != MotivoPuntos.AJUSTE) {
			throw new IllegalArgumentException("Los puntos de un " + motivo + " tienen el signo equivocado");
		}
		if ((referenciaTipo == null) != (referenciaId == null)) {
			throw new IllegalArgumentException("La referencia lleva tipo e id, o ninguno");
		}
		this.puntos = puntos;
		this.referenciaTipo = referenciaTipo;
		this.referenciaId = referenciaId;
		this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public int getPuntos() {
		return puntos;
	}

	public MotivoPuntos getMotivo() {
		return motivo;
	}

	public String getReferenciaTipo() {
		return referenciaTipo;
	}

	public Long getReferenciaId() {
		return referenciaId;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
