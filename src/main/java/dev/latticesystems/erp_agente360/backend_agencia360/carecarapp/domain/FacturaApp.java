package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * La factura simulada de un pago de la app (V2-M08.06, tabla {@code factura} de
 * la ficha, que comparte con las de la venta de una unidad del ERP). Copia el
 * emisor, el receptor y los montos como se timbraron y guarda las claves del
 * XML y del PDF en {@code archivos}. Una factura timbrada no se modifica ni se
 * borra. La orden, el pedido, el cliente y el perfil fiscal se mapean solo como
 * ids para no cruzar módulos.
 */
@Entity
@Table(name = "factura")
@SQLRestriction("tipo <> 'VENTA_UNIDAD'")
public class FacturaApp {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String folio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 15)
	private ObjetoFacturado tipo;

	@Column(name = "cliente_id", nullable = false, updatable = false)
	private Long clienteId;

	@Column(name = "orden_servicio_id", updatable = false)
	private Long ordenId;

	@Column(name = "pedido_app_id", updatable = false)
	private Long pedidoId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pago_app_id", nullable = false, updatable = false)
	private PagoApp pago;

	@Column(name = "perfil_fiscal_id", updatable = false)
	private Long perfilFiscalId;

	@Column(name = "emisor_rfc", nullable = false, updatable = false, length = 13)
	private String emisorRfc;

	@Column(name = "emisor_nombre", nullable = false, updatable = false, length = 250)
	private String emisorNombre;

	@Column(name = "emisor_domicilio_fiscal", nullable = false, updatable = false, length = 300)
	private String emisorCodigoPostal;

	@Column(name = "emisor_regimen_fiscal", nullable = false, updatable = false, length = 150)
	private String emisorRegimenFiscal;

	@Column(name = "receptor_rfc", nullable = false, updatable = false, length = 13)
	private String receptorRfc;

	@Column(name = "receptor_nombre", nullable = false, updatable = false, length = 250)
	private String receptorNombre;

	@Column(name = "receptor_domicilio_fiscal", nullable = false, updatable = false, length = 300)
	private String receptorCodigoPostal;

	@Column(name = "receptor_regimen_fiscal", nullable = false, updatable = false, length = 150)
	private String receptorRegimenFiscal;

	@Column(name = "receptor_uso_cfdi", nullable = false, updatable = false, length = 4)
	private String usoCfdi;

	@Column(name = "forma_pago", nullable = false, updatable = false, length = 2)
	private String formaPago;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal iva;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Column(name = "uuid_fiscal", nullable = false, updatable = false)
	private UUID uuidFiscal;

	@Column(name = "xml_archivo_id", nullable = false, updatable = false, length = 80)
	private String xmlArchivoId;

	@Column(name = "pdf_archivo_id", nullable = false, updatable = false, length = 80)
	private String pdfArchivoId;

	@Column(name = "timbrada_en", nullable = false, updatable = false)
	private Instant timbradaEn;

	@Column(name = "emitida_en", nullable = false, updatable = false)
	private Instant emitidaEn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant creadaEn;

	@Column(name = "updated_at", nullable = false)
	private Instant actualizadaEn;

	protected FacturaApp() {
		// Requerido por JPA
	}

	/**
	 * La factura que se acaba de timbrar para un pago; queda ligada a él.
	 *
	 * @param uuid
	 *            el folio fiscal del timbre
	 * @param timbradaEn
	 *            cuándo se timbró
	 * @param claveXml
	 *            dónde quedó el XML en {@code archivos}
	 * @param clavePdf
	 *            dónde quedó el PDF en {@code archivos}
	 */
	public static FacturaApp timbrada(PagoApp pago, Long perfilFiscalId, Comprobante comprobante, UUID uuid,
			Instant timbradaEn, String claveXml, String clavePdf) {
		Objects.requireNonNull(pago, "pago");
		FacturaApp factura = new FacturaApp();
		factura.folio = comprobante.folioCompleto();
		factura.tipo = pago.getOrdenId() != null ? ObjetoFacturado.SERVICIO : ObjetoFacturado.PEDIDO_APP;
		factura.clienteId = pago.getClienteId();
		factura.ordenId = pago.getOrdenId();
		factura.pedidoId = pago.getPedidoId();
		factura.pago = pago;
		factura.perfilFiscalId = perfilFiscalId;
		factura.emisorRfc = comprobante.emisor().rfc();
		factura.emisorNombre = comprobante.emisor().nombre();
		factura.emisorCodigoPostal = comprobante.emisor().codigoPostal();
		factura.emisorRegimenFiscal = comprobante.emisor().regimenFiscal();
		factura.receptorRfc = comprobante.receptor().rfc();
		factura.receptorNombre = comprobante.receptor().nombre();
		factura.receptorCodigoPostal = comprobante.receptor().codigoPostal();
		factura.receptorRegimenFiscal = comprobante.receptor().regimenFiscal();
		factura.usoCfdi = comprobante.receptor().usoCfdi();
		factura.formaPago = comprobante.formaPago();
		factura.subtotal = comprobante.subtotal();
		factura.iva = comprobante.iva();
		factura.total = comprobante.total();
		factura.uuidFiscal = Objects.requireNonNull(uuid, "uuid");
		factura.xmlArchivoId = Objects.requireNonNull(claveXml, "claveXml");
		factura.pdfArchivoId = Objects.requireNonNull(clavePdf, "clavePdf");
		factura.timbradaEn = Objects.requireNonNull(timbradaEn, "timbradaEn");
		factura.emitidaEn = timbradaEn;
		factura.creadaEn = timbradaEn;
		factura.actualizadaEn = timbradaEn;
		pago.ligarFactura(factura);
		return factura;
	}

	public Long getId() {
		return id;
	}

	public String getFolio() {
		return folio;
	}

	public ObjetoFacturado getTipo() {
		return tipo;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public Long getOrdenId() {
		return ordenId;
	}

	public Long getPedidoId() {
		return pedidoId;
	}

	public Long getPagoId() {
		return pago.getId();
	}

	public Long getPerfilFiscalId() {
		return perfilFiscalId;
	}

	public String getReceptorRfc() {
		return receptorRfc;
	}

	public String getReceptorNombre() {
		return receptorNombre;
	}

	public String getUsoCfdi() {
		return usoCfdi;
	}

	public String getFormaPago() {
		return formaPago;
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

	public UUID getUuidFiscal() {
		return uuidFiscal;
	}

	public String getXmlArchivoId() {
		return xmlArchivoId;
	}

	public String getPdfArchivoId() {
		return pdfArchivoId;
	}

	public Instant getTimbradaEn() {
		return timbradaEn;
	}
}
