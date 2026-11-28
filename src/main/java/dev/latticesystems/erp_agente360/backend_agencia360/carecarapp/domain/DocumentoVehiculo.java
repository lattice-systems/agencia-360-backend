package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Documento de un vehículo del cliente: póliza, tarjeta de circulación,
 * verificación… (V2-M09.01, ficha sección 9). El estado se calcula con la fecha
 * de vencimiento y el día que se pregunte; no se guarda.
 */
@Entity
@Table(name = "documento_vehiculo")
public class DocumentoVehiculo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehiculo_cliente_id", nullable = false)
	private Long vehiculoClienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TipoDocumento tipo;

	@Column(length = 80)
	private String titulo;

	@Column(length = 60)
	private String numero;

	@Column(name = "emitido_en")
	private LocalDate emitidoEn;

	@Column(name = "vence_en")
	private LocalDate venceEn;

	@Column(name = "archivo_url", length = 500)
	private String archivoUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private OrigenDocumento origen;

	@Column(length = 80)
	private String aseguradora;

	@Column(name = "telefono_aseguradora", length = 30)
	private String telefonoAseguradora;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn = OffsetDateTime.now();

	protected DocumentoVehiculo() {
	}

	/**
	 * La aseguradora y su teléfono solo se guardan en la póliza.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el vehículo, el tipo o el origen, si {@code OTRO} no
	 *             lleva título o si el vencimiento es anterior a la emisión
	 */
	public DocumentoVehiculo(Long vehiculoClienteId, TipoDocumento tipo, String titulo, String numero,
			LocalDate emitidoEn, LocalDate venceEn, OrigenDocumento origen, String aseguradora,
			String telefonoAseguradora) {
		if (vehiculoClienteId == null || tipo == null || origen == null) {
			throw new IllegalArgumentException("El documento necesita vehículo, tipo y origen");
		}
		if (tipo == TipoDocumento.OTRO && (titulo == null || titulo.isBlank())) {
			throw new IllegalArgumentException("Un documento «otro» necesita título");
		}
		if (emitidoEn != null && venceEn != null && venceEn.isBefore(emitidoEn)) {
			throw new IllegalArgumentException("El vencimiento no puede ser anterior a la emisión");
		}
		this.vehiculoClienteId = vehiculoClienteId;
		this.tipo = tipo;
		this.titulo = limpio(titulo);
		this.numero = limpio(numero);
		this.emitidoEn = emitidoEn;
		this.venceEn = venceEn;
		this.origen = origen;
		boolean poliza = tipo == TipoDocumento.POLIZA;
		this.aseguradora = poliza ? limpio(aseguradora) : null;
		this.telefonoAseguradora = poliza ? limpio(telefonoAseguradora) : null;
	}

	private static String limpio(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
	}

	/** Estado según la fecha dada (la de la agencia, no la del teléfono). */
	public EstadoDocumento estadoEn(LocalDate hoy) {
		Integer dias = diasRestantesEn(hoy);
		if (dias == null) {
			return EstadoDocumento.SIN_FECHA;
		}
		if (dias < 0) {
			return EstadoDocumento.VENCIDO;
		}
		return dias <= EstadoDocumento.DIAS_POR_VENCER ? EstadoDocumento.POR_VENCER : EstadoDocumento.VIGENTE;
	}

	/**
	 * Días que faltan para vencer (negativo si ya venció); {@code null} sin fecha.
	 */
	public Integer diasRestantesEn(LocalDate hoy) {
		return venceEn == null ? null : (int) ChronoUnit.DAYS.between(hoy, venceEn);
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public TipoDocumento getTipo() {
		return tipo;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getNumero() {
		return numero;
	}

	public LocalDate getEmitidoEn() {
		return emitidoEn;
	}

	public LocalDate getVenceEn() {
		return venceEn;
	}

	/** Asocia la clave que devuelve el almacén privado. */
	public void adjuntarArchivo(String clave) {
		this.archivoUrl = clave;
	}

	public String getArchivoUrl() {
		return archivoUrl;
	}

	public OrigenDocumento getOrigen() {
		return origen;
	}

	public String getAseguradora() {
		return aseguradora;
	}

	public String getTelefonoAseguradora() {
		return telefonoAseguradora;
	}

	public OffsetDateTime getCreadoEn() {
		return creadoEn;
	}
}
