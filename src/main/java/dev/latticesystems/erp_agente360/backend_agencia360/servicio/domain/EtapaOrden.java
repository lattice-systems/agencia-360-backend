package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
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
 * Una etapa de la línea de tiempo de la orden (V2-M04.02): cuándo empezó,
 * cuándo terminó y una nota opcional del taller.
 */
@Entity
@Table(name = "etapa_orden")
public class EtapaOrden extends Auditable {

	static final int NOTA_MAXIMA = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "orden_servicio_id", nullable = false)
	private Long ordenServicioId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 25)
	private EtapaTaller etapa;

	@Column(nullable = false)
	private Instant inicio;

	private Instant fin;

	@Column(length = NOTA_MAXIMA)
	private String nota;

	protected EtapaOrden() {
	}

	private EtapaOrden(Long ordenServicioId, EtapaTaller etapa, Instant inicio, String nota) {
		this.ordenServicioId = ordenServicioId;
		this.etapa = etapa;
		this.inicio = inicio;
		this.nota = nota;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si la nota pasa de 300 caracteres
	 */
	public static EtapaOrden empezar(Long ordenServicioId, EtapaTaller etapa, Instant inicio, String nota) {
		Objects.requireNonNull(ordenServicioId, "ordenServicioId");
		Objects.requireNonNull(etapa, "etapa");
		Objects.requireNonNull(inicio, "inicio");
		String limpia = nota == null || nota.isBlank() ? null : nota.strip();
		if (limpia != null && limpia.length() > NOTA_MAXIMA) {
			throw new IllegalArgumentException("La nota admite hasta " + NOTA_MAXIMA + " caracteres");
		}
		return new EtapaOrden(ordenServicioId, etapa, inicio, limpia);
	}

	public void terminar(Instant momento) {
		if (fin == null) {
			fin = momento.isBefore(inicio) ? inicio : momento;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getOrdenServicioId() {
		return ordenServicioId;
	}

	public EtapaTaller getEtapa() {
		return etapa;
	}

	public Instant getInicio() {
		return inicio;
	}

	public Instant getFin() {
		return fin;
	}

	public String getNota() {
		return nota;
	}
}
