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
 * Un amigo que el cliente refirió (V2-M12.04). Nace registrado con el
 * consentimiento del amigo; pasa a seguimiento cuando ventascrm crea su
 * prospecto y a compró cuando la venta se finaliza, momento en que el cliente
 * gana sus puntos una sola vez.
 */
@Entity
@Table(name = "referido")
public class Referido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false)
	private Long clienteId;

	@Column(nullable = false, length = 12)
	private String codigo;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 10)
	private String telefono;

	@Column(length = 254)
	private String correo;

	@Column(name = "consentimiento_en", nullable = false)
	private Instant consentimientoEn;

	@Column(name = "prospecto_id")
	private Long prospectoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private EstadoReferido estado;

	@Column(name = "recompensado_en")
	private Instant recompensadoEn;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	protected Referido() {
	}

	/**
	 * @param telefono
	 *            10 dígitos, o {@code null} si hay correo
	 * @throws IllegalArgumentException
	 *             sin nombre o sin teléfono ni correo
	 */
	public Referido(Long clienteId, String codigo, String nombre, String telefono, String correo, Instant ahora) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.codigo = Objects.requireNonNull(codigo, "codigo");
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("El nombre del referido es obligatorio");
		}
		if (telefono == null && correo == null) {
			throw new IllegalArgumentException("El referido necesita teléfono o correo");
		}
		this.nombre = nombre.strip();
		this.telefono = telefono;
		this.correo = correo;
		this.consentimientoEn = Objects.requireNonNull(ahora, "ahora");
		this.creadoEn = ahora;
		this.estado = EstadoReferido.REGISTRADO;
	}

	/** ventascrm ya creó su prospecto. */
	public void ligarProspecto(Long prospecto) {
		this.prospectoId = Objects.requireNonNull(prospecto, "prospecto");
		if (estado == EstadoReferido.REGISTRADO) {
			estado = EstadoReferido.EN_SEGUIMIENTO;
		}
	}

	/**
	 * La venta del prospecto se finalizó.
	 *
	 * @return {@code true} si es la primera vez y hay que dar los puntos
	 */
	public boolean marcarCompra(Instant ahora) {
		if (estado == EstadoReferido.COMPRO) {
			return false;
		}
		estado = EstadoReferido.COMPRO;
		recompensadoEn = Objects.requireNonNull(ahora, "ahora");
		return true;
	}

	/** El prospecto se cerró sin venta. */
	public void descartar() {
		if (estado.abierto()) {
			estado = EstadoReferido.DESCARTADO;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public String getTelefono() {
		return telefono;
	}

	public String getCorreo() {
		return correo;
	}

	public Long getProspectoId() {
		return prospectoId;
	}

	public EstadoReferido getEstado() {
		return estado;
	}

	public Instant getRecompensadoEn() {
		return recompensadoEn;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
