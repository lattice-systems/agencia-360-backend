package dev.latticesystems.erp_agente360.backend_agencia360.facturacion.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Factura de la venta de una unidad (ERP-1.3), con folio interno y PDF, sin
 * timbrado (decisión 6 del modelo de datos). Copia los datos del emisor, del
 * receptor y de la unidad como estaban al emitir; una factura emitida no se
 * modifica.
 *
 * <p>
 * El prospecto, la venta y la unidad se guardan solo como id para no cruzar
 * módulos; las llaves foráneas viven en la base.
 */
@Entity
@Table(name = "factura")
public class Factura extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String folio;

	@Column(name = "prospecto_id", nullable = false, unique = true)
	private Long prospectoId;

	@Column(name = "venta_id", nullable = false, unique = true)
	private Long ventaId;

	@Column(name = "vehiculo_id", nullable = false, unique = true)
	private Long vehiculoId;

	@Column(name = "emisor_rfc", nullable = false)
	private String emisorRfc;

	@Column(name = "emisor_nombre", nullable = false)
	private String emisorNombre;

	@Column(name = "emisor_domicilio_fiscal", nullable = false)
	private String emisorDomicilioFiscal;

	@Column(name = "emisor_regimen_fiscal", nullable = false)
	private String emisorRegimenFiscal;

	@Column(name = "receptor_rfc", nullable = false)
	private String receptorRfc;

	@Column(name = "receptor_nombre", nullable = false)
	private String receptorNombre;

	@Column(name = "receptor_domicilio_fiscal", nullable = false)
	private String receptorDomicilioFiscal;

	@Column(name = "receptor_regimen_fiscal", nullable = false)
	private String receptorRegimenFiscal;

	@Column(name = "unidad_vin", nullable = false)
	private String vin;

	@Column(name = "unidad_marca", nullable = false)
	private String marca;

	/** Tipo de la unidad (Sentra), como en la factura. */
	@Column(name = "unidad_modelo", nullable = false)
	private String modelo;

	@Column(name = "unidad_version", nullable = false)
	private String version;

	/** Modelo de la unidad (2026), como en la factura. */
	@Column(name = "unidad_anio", nullable = false)
	private Integer anio;

	@Column(name = "unidad_color", nullable = false)
	private String color;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal iva;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	/** Unos cuantos KB; se guarda tal como se le entregó al cliente. */
	@Column(nullable = false)
	private byte[] pdf;

	@Column(name = "emitida_por", nullable = false)
	private Long emitidaPor;

	@Column(name = "emitida_en", nullable = false)
	private Instant emitidaEn;

	protected Factura() {
		// Requerido por JPA
	}

	/**
	 * @param pdf
	 *            el PDF que se le entrega al cliente, armado con estos mismos datos
	 */
	public static Factura emitir(DatosFactura datos, byte[] pdf, Long usuarioId) {
		Objects.requireNonNull(datos, "datos");
		Factura factura = new Factura();
		factura.folio = datos.folio();
		factura.prospectoId = datos.prospectoId();
		factura.ventaId = datos.ventaId();
		factura.vehiculoId = datos.unidad().vehiculoId();
		factura.emisorRfc = datos.emisor().rfc();
		factura.emisorNombre = datos.emisor().nombre();
		factura.emisorDomicilioFiscal = datos.emisor().domicilioFiscal();
		factura.emisorRegimenFiscal = datos.emisor().regimenFiscal();
		factura.receptorRfc = datos.receptor().rfc();
		factura.receptorNombre = datos.receptor().nombre();
		factura.receptorDomicilioFiscal = datos.receptor().domicilioFiscal();
		factura.receptorRegimenFiscal = datos.receptor().regimenFiscal();
		factura.vin = datos.unidad().vin();
		factura.marca = datos.unidad().marca();
		factura.modelo = datos.unidad().modelo();
		factura.version = datos.unidad().version();
		factura.anio = datos.unidad().anio();
		factura.color = datos.unidad().color();
		factura.subtotal = datos.montos().subtotal();
		factura.iva = datos.montos().iva();
		factura.total = datos.montos().total();
		factura.pdf = Objects.requireNonNull(pdf, "pdf");
		factura.emitidaPor = Objects.requireNonNull(usuarioId, "usuarioId");
		factura.emitidaEn = datos.emitidaEn();
		return factura;
	}

	public Long getId() {
		return id;
	}

	public String getFolio() {
		return folio;
	}

	public Long getProspectoId() {
		return prospectoId;
	}

	public Long getVentaId() {
		return ventaId;
	}

	public Long getVehiculoId() {
		return vehiculoId;
	}

	public String getEmisorRfc() {
		return emisorRfc;
	}

	public String getReceptorRfc() {
		return receptorRfc;
	}

	public String getReceptorNombre() {
		return receptorNombre;
	}

	public String getVin() {
		return vin;
	}

	public String getMarca() {
		return marca;
	}

	public String getModelo() {
		return modelo;
	}

	public String getVersion() {
		return version;
	}

	public Integer getAnio() {
		return anio;
	}

	public String getColor() {
		return color;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getIva() {
		return iva;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public byte[] getPdf() {
		return pdf;
	}

	public Long getEmitidaPor() {
		return emitidaPor;
	}

	public Instant getEmitidaEn() {
		return emitidaEn;
	}
}
