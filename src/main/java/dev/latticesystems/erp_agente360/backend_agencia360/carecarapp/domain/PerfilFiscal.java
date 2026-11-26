package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Datos fiscales del cliente para facturar sus servicios (V2-M08.05, tabla
 * {@code perfil_fiscal} de la ficha). Un cliente puede tener varios; uno es el
 * predeterminado, y la base no deja que haya dos. {@code clienteId} solo se
 * mapea como id para no cruzar módulos.
 */
@Entity
@Table(name = "perfil_fiscal")
public class PerfilFiscal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cliente_id", nullable = false, updatable = false)
	private Long clienteId;

	@Column(nullable = false, length = 13)
	private String rfc;

	@Column(name = "razon_social", nullable = false, length = 254)
	private String razonSocial;

	@Column(name = "regimen_fiscal", nullable = false, length = 3)
	private String regimenFiscal;

	@Column(name = "codigo_postal", nullable = false, length = 5)
	private String codigoPostal;

	@Column(name = "uso_cfdi", nullable = false, length = 4)
	private String usoCfdi;

	@Column(length = 254)
	private String correo;

	@Column(nullable = false)
	private boolean predeterminado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	protected PerfilFiscal() {
		// Requerido por JPA
	}

	public PerfilFiscal(Long clienteId, DatosFiscales datos, Instant ahora) {
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId");
		this.creadoEn = Objects.requireNonNull(ahora, "ahora");
		corregir(datos, ahora);
	}

	/** Reemplaza los datos fiscales, ya revisados. */
	public void corregir(DatosFiscales datos, Instant ahora) {
		Objects.requireNonNull(datos, "datos");
		this.rfc = datos.rfc();
		this.razonSocial = datos.razonSocial();
		this.regimenFiscal = datos.regimenFiscal();
		this.codigoPostal = datos.codigoPostal();
		this.usoCfdi = datos.usoCfdi();
		this.correo = datos.correo();
		this.actualizadoEn = ahora;
	}

	public void marcarPredeterminado(boolean predeterminado) {
		this.predeterminado = predeterminado;
	}

	/** Lo dice el largo del RFC: 13 es persona física y 12 moral. */
	public TipoPersona getTipo() {
		return Rfc.tipoDe(rfc).orElseThrow();
	}

	public Long getId() {
		return id;
	}

	public Long getClienteId() {
		return clienteId;
	}

	public String getRfc() {
		return rfc;
	}

	public String getRazonSocial() {
		return razonSocial;
	}

	public String getRegimenFiscal() {
		return regimenFiscal;
	}

	public String getCodigoPostal() {
		return codigoPostal;
	}

	public String getUsoCfdi() {
		return usoCfdi;
	}

	public String getCorreo() {
		return correo;
	}

	public boolean isPredeterminado() {
		return predeterminado;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
