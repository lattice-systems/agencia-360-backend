package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
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
 * Solicitud de asistencia (SOS) que el cliente pide desde la app (V2-M11.01).
 * Nace {@code RECIBIDA}. La ubicación es opcional: latitud y longitud van
 * juntas o ninguna.
 *
 * <p>
 * {@code clienteId} y {@code vehiculoClienteId} solo se mapean como ids para no
 * cruzar módulos; las llaves foráneas sí viven en la base.
 */
@Entity
@Table(name = "solicitud_asistencia")
public class SolicitudAsistencia {

	/** Lo más largo que puede ser el comentario. */
	public static final int COMENTARIO_MAXIMO = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private TipoAsistencia tipo;

	@Column(precision = 9, scale = 6)
	private BigDecimal lat;

	@Column(precision = 9, scale = 6)
	private BigDecimal lng;

	@Column(length = COMENTARIO_MAXIMO)
	private String comentario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private EstadoAsistencia estado;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	@Column(name = "cerrada_en")
	private Instant cerradaEn;

	protected SolicitudAsistencia() {
		// Requerido por JPA
	}

	private SolicitudAsistencia(Long clienteId, Long vehiculoClienteId, TipoAsistencia tipo, BigDecimal lat,
			BigDecimal lng, String comentario, Instant creadaEn) {
		this.clienteId = clienteId;
		this.vehiculoClienteId = vehiculoClienteId;
		this.tipo = tipo;
		this.lat = lat;
		this.lng = lng;
		this.comentario = comentario;
		this.estado = EstadoAsistencia.RECIBIDA;
		this.creadaEn = creadaEn;
	}

	/**
	 * Una solicitud recibida.
	 *
	 * @param lat
	 *            entre -90 y 90; nula si no se comparte la ubicación
	 * @param lng
	 *            entre -180 y 180; nula si no se comparte la ubicación
	 * @param comentario
	 *            hasta 500 caracteres; en blanco se guarda como nulo
	 * @throws IllegalArgumentException
	 *             si falta un dato, solo viene una coordenada, una está fuera de
	 *             rango o el comentario es muy largo (400)
	 */
	public static SolicitudAsistencia recibir(Long clienteId, Long vehiculoClienteId, TipoAsistencia tipo,
			BigDecimal lat, BigDecimal lng, String comentario, Instant creadaEn) {
		Objects.requireNonNull(clienteId, "clienteId");
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		Objects.requireNonNull(creadaEn, "creadaEn");
		if (tipo == null) {
			throw new IllegalArgumentException("El tipo de asistencia es obligatorio");
		}
		if ((lat == null) != (lng == null)) {
			throw new IllegalArgumentException("La latitud y la longitud van juntas");
		}
		if (lat != null && (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
				|| lng.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
			throw new IllegalArgumentException("La ubicación está fuera de rango");
		}
		String limpio = comentario == null || comentario.isBlank() ? null : comentario.strip();
		if (limpio != null && limpio.length() > COMENTARIO_MAXIMO) {
			throw new IllegalArgumentException("El comentario pasa de " + COMENTARIO_MAXIMO + " caracteres");
		}
		return new SolicitudAsistencia(clienteId, vehiculoClienteId, tipo, lat, lng, limpio, creadaEn);
	}

	/**
	 * Mueve la solicitud al siguiente estado. Al cerrarse (atendida o cancelada)
	 * guarda cuándo y borra la ubicación: solo se conserva mientras hay ayuda por
	 * llegar.
	 *
	 * @throws ConflictoException
	 *             con {@code TRANSICION_INVALIDA} si el paso no está permitido (409)
	 */
	public void cambiarEstado(EstadoAsistencia nuevo, Instant en) {
		Objects.requireNonNull(nuevo, "nuevo");
		Objects.requireNonNull(en, "en");
		if (!estado.puedePasarA(nuevo)) {
			throw new ConflictoException(
					"Una solicitud " + estado.valor() + " no puede pasar a " + nuevo.valor(),
					CodigosError.TRANSICION_INVALIDA);
		}
		this.estado = nuevo;
		if (!nuevo.abierta()) {
			this.cerradaEn = en;
			this.lat = null;
			this.lng = null;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public TipoAsistencia getTipo() {
		return tipo;
	}

	public BigDecimal getLat() {
		return lat;
	}

	public BigDecimal getLng() {
		return lng;
	}

	public String getComentario() {
		return comentario;
	}

	public EstadoAsistencia getEstado() {
		return estado;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public Instant getCerradaEn() {
		return cerradaEn;
	}
}
