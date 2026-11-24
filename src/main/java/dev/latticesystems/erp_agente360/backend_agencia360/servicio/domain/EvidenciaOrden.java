package dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.TipoEvidencia;
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

/**
 * Foto o video del técnico sobre la orden o sobre un punto revisado
 * (V2-M04.04). {@code url} guarda la clave del archivo en el almacén
 * ({@code archivos}); la app recibe una URL temporal firmada.
 */
@Entity
@Table(name = "evidencia_orden")
public class EvidenciaOrden extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "orden_servicio_id", nullable = false)
	private Long ordenServicioId;

	@Column(name = "punto_inspeccion_id")
	private Long puntoInspeccionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TipoEvidencia tipo;

	@Column(nullable = false, length = 300)
	private String url;

	protected EvidenciaOrden() {
	}

	private EvidenciaOrden(Long ordenServicioId, Long puntoInspeccionId, TipoEvidencia tipo, String url) {
		this.ordenServicioId = ordenServicioId;
		this.puntoInspeccionId = puntoInspeccionId;
		this.tipo = tipo;
		this.url = url;
	}

	public static EvidenciaOrden de(Long ordenServicioId, Long puntoInspeccionId, TipoEvidencia tipo,
			String claveArchivo) {
		Objects.requireNonNull(ordenServicioId, "ordenServicioId");
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(claveArchivo, "claveArchivo");
		return new EvidenciaOrden(ordenServicioId, puntoInspeccionId, tipo, claveArchivo);
	}

	/**
	 * @return el tipo de evidencia según el tipo de contenido del archivo
	 * @throws IllegalArgumentException
	 *             si no es una imagen JPEG, PNG o WebP ni un video MP4
	 */
	public static TipoEvidencia tipoDe(String tipoContenido) {
		if (tipoContenido != null && tipoContenido.startsWith("image/")) {
			return TipoEvidencia.FOTO;
		}
		if ("video/mp4".equals(tipoContenido)) {
			return TipoEvidencia.VIDEO;
		}
		throw new IllegalArgumentException("La evidencia debe ser una foto JPEG, PNG o WebP o un video MP4");
	}

	public Long getId() {
		return id;
	}

	public Long getOrdenServicioId() {
		return ordenServicioId;
	}

	public Long getPuntoInspeccionId() {
		return puntoInspeccionId;
	}

	public TipoEvidencia getTipo() {
		return tipo;
	}

	public String getUrl() {
		return url;
	}
}
