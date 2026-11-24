package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.ResultadoInspeccion;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/** Un punto de la inspección multipunto con su semáforo (V2-M04.04). */
@Entity
@Table(name = "punto_inspeccion")
public class PuntoInspeccion extends Auditable {

	static final int PUNTO_MAXIMO = 120;
	static final int COMENTARIO_MAXIMO = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "orden_servicio_id", nullable = false)
	private Long ordenServicioId;

	@Column(nullable = false, length = PUNTO_MAXIMO)
	private String punto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ResultadoInspeccion resultado;

	@Column(length = COMENTARIO_MAXIMO)
	private String comentario;

	protected PuntoInspeccion() {
	}

	private PuntoInspeccion(Long ordenServicioId, String punto, ResultadoInspeccion resultado, String comentario) {
		this.ordenServicioId = ordenServicioId;
		this.punto = punto;
		this.resultado = resultado;
		this.comentario = comentario;
	}

	/**
	 * @throws IllegalArgumentException
	 *             si el punto viene vacío o pasa de 120 caracteres, o el comentario
	 *             pasa de 500
	 */
	public static PuntoInspeccion revisar(Long ordenServicioId, String punto, ResultadoInspeccion resultado,
			String comentario) {
		Objects.requireNonNull(ordenServicioId, "ordenServicioId");
		if (resultado == null) {
			throw new IllegalArgumentException("El resultado del punto es obligatorio");
		}
		String nombre = punto == null ? "" : punto.strip();
		if (nombre.isEmpty() || nombre.length() > PUNTO_MAXIMO) {
			throw new IllegalArgumentException(
					"El punto es obligatorio y admite hasta " + PUNTO_MAXIMO + " caracteres");
		}
		String detalle = comentario == null || comentario.isBlank() ? null : comentario.strip();
		if (detalle != null && detalle.length() > COMENTARIO_MAXIMO) {
			throw new IllegalArgumentException("El comentario admite hasta " + COMENTARIO_MAXIMO + " caracteres");
		}
		return new PuntoInspeccion(ordenServicioId, nombre, resultado, detalle);
	}

	public Long getId() {
		return id;
	}

	public Long getOrdenServicioId() {
		return ordenServicioId;
	}

	public String getPunto() {
		return punto;
	}

	public ResultadoInspeccion getResultado() {
		return resultado;
	}

	public String getComentario() {
		return comentario;
	}
}
