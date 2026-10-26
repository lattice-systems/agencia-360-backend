package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Persona interesada en comprar, a cargo de un vendedor (CRM-1.0). Avanza por
 * etapas y cada cambio queda en su historial (CRM-1.1). La fecha y hora de
 * registro es {@code created_at} de {@link Auditable}.
 *
 * <p>
 * Quién mueve cada etapa lo decidió el PO el 12 de octubre (ver {@link Etapa}).
 * Una regla que no se cumple responde 409.
 *
 * <p>
 * El vendedor se guarda solo como id: el usuario pertenece al módulo de acceso
 * y aquí no se navega hacia él.
 */
@Entity
@Table(name = "prospecto")
public class Prospecto extends Auditable {

	private static final Pattern SEPARADORES_TELEFONO = Pattern.compile("[\\s().-]");
	private static final Pattern DIEZ_DIGITOS = Pattern.compile("\\d{10}");
	private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999999.99");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 20)
	private String telefono;

	@Column(length = 120)
	private String correo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Etapa etapa;

	@Column(name = "vendedor_id", nullable = false)
	private Long vendedorId;

	/**
	 * Obligatorio al cerrar (decisión del PO del 12 de octubre); uno de los tres
	 * motivos fijos de CRM-1.2.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_cierre", length = 30)
	private MotivoCierre motivoCierre;

	@Enumerated(EnumType.STRING)
	@Column(name = "ultima_etapa", length = 20)
	private Etapa ultimaEtapa;

	/** Fecha y hora del último cambio de etapa ("Último cambio" en el CRM). */
	@Column(name = "etapa_actualizada_en", nullable = false)
	private Instant etapaActualizadaEn;

	/**
	 * Precio total de la venta (ERP-1.1): lo captura el vendedor al pasar a Pago y
	 * Contabilidad lo puede corregir al validar los pagos.
	 */
	@Column(name = "precio_total", precision = 12, scale = 2)
	private BigDecimal precioTotal;

	/**
	 * Datos fiscales del cliente para la factura (ERP-1.3): los captura el vendedor
	 * al pasar a Pago. Nulo si todavía no los tiene.
	 */
	@Embedded
	private DatosFiscales datosFiscales;

	/** Hay una solicitud de cierre esperando al Administrador. */
	@Column(name = "cierre_pendiente", nullable = false)
	private boolean cierrePendiente;

	@OneToMany(mappedBy = "prospecto", cascade = CascadeType.PERSIST)
	@OrderBy("fechaHora asc, id asc")
	private List<HistorialEtapa> historial = new ArrayList<>();

	protected Prospecto() {
		// Requerido por JPA
	}

	private Prospecto(String nombre, String telefono, String correo, Long vendedorId) {
		this.nombre = nombre;
		this.telefono = telefono;
		this.correo = correo;
		this.vendedorId = vendedorId;
	}

	/**
	 * Registra un prospecto en "Primer contacto" y escribe el primer movimiento de
	 * su historial (CRM-1.0). Quita espacios sobrantes, trata los campos vacíos
	 * como no capturados, guarda el teléfono solo con sus 10 dígitos y el correo en
	 * minúsculas para poder comparar repetidos.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el nombre, faltan a la vez el teléfono y el correo, o el
	 *             teléfono no es de 10 dígitos (decisión del PO del 12 de octubre:
	 *             solo números de México)
	 */
	public static Prospecto registrar(String nombre, String telefono, String correo, Long vendedorId, Instant ahora) {
		String nombreLimpio = limpiar(nombre);
		String telefonoLimpio = normalizarTelefono(telefono);
		String correoLimpio = normalizarCorreo(correo);
		if (nombreLimpio == null) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}
		if (telefonoLimpio == null && correoLimpio == null) {
			throw new IllegalArgumentException("Captura al menos un teléfono o un correo");
		}
		Objects.requireNonNull(vendedorId, "vendedorId");

		Prospecto prospecto = new Prospecto(nombreLimpio, telefonoLimpio, correoLimpio, vendedorId);
		prospecto.moverA(Etapa.PRIMER_CONTACTO, OrigenCambio.CRM, vendedorId, ahora);
		return prospecto;
	}

	/**
	 * Cambio de etapa que hace el vendedor desde el CRM (CRM-1.1):
	 * <ul>
	 * <li>Desde Primer contacto, Visita o Cotización puede ir a cualquiera de las
	 * tres, a Pago o a Cerrado (con motivo).</li>
	 * <li>Desde Entrega solo puede finalizar.</li>
	 * <li>En Pago y Facturación no mueve nada; para cerrar desde Pago en adelante
	 * envía una {@link SolicitudCierre}.</li>
	 * </ul>
	 *
	 * @return {@code false} si el prospecto ya estaba en esa etapa; en ese caso no
	 *         se escribe historial
	 * @throws ConflictoException
	 *             si el cambio no le corresponde al vendedor
	 * @throws IllegalArgumentException
	 *             si cierra sin motivo o con {@link MotivoCierre#SIN_CATEGORIA}
	 */
	public boolean moverPorVendedor(Etapa nueva, MotivoCierre motivoCierre, Long usuarioId, Instant ahora) {
		Objects.requireNonNull(nueva, "nueva");
		if (nueva == etapa) {
			return false;
		}
		if (etapa.esInicial()) {
			if (nueva == Etapa.CERRADO) {
				cerrar(motivoCierre, OrigenCambio.CRM, usuarioId, ahora);
				return true;
			}
			if (nueva.esInicial() || nueva == Etapa.PAGO) {
				moverA(nueva, OrigenCambio.CRM, usuarioId, ahora);
				return true;
			}
			throw new ConflictoException("Desde " + nombreDe(etapa)
					+ " solo puedes pasar a Primer contacto, Visita, Cotización, Pago o Cerrado");
		}
		if (etapa == Etapa.ENTREGA && nueva == Etapa.FINALIZADO) {
			moverA(Etapa.FINALIZADO, OrigenCambio.CRM, usuarioId, ahora);
			return true;
		}
		throw new ConflictoException(motivoDeBloqueo(nueva));
	}

	/**
	 * Contabilidad valida el pago desde el ERP y el prospecto pasa de Pago a
	 * Facturación.
	 *
	 * @throws ConflictoException
	 *             si el prospecto no está en Pago
	 */
	public void validarPago(Long usuarioId, Instant ahora) {
		exigirEtapa(Etapa.PAGO, "Solo se valida el pago de un prospecto en Pago");
		moverA(Etapa.FACTURACION, OrigenCambio.ERP, usuarioId, ahora);
	}

	/**
	 * Facturación emite la factura desde el ERP y el prospecto pasa a Entrega.
	 *
	 * @throws ConflictoException
	 *             si el prospecto no está en Facturación
	 */
	public void confirmarFacturacion(Long usuarioId, Instant ahora) {
		exigirEtapa(Etapa.FACTURACION, "Solo se factura un prospecto en Facturación");
		moverA(Etapa.ENTREGA, OrigenCambio.ERP, usuarioId, ahora);
	}

	/**
	 * Guarda el precio total de la venta (ERP-1.1).
	 *
	 * @throws IllegalArgumentException
	 *             si falta, no es mayor a cero o tiene más de dos decimales
	 */
	public void fijarPrecioTotal(BigDecimal precio) {
		this.precioTotal = validarPrecioTotal(precio);
	}

	/**
	 * @return el precio con dos decimales
	 * @throws IllegalArgumentException
	 *             si falta, no es mayor a cero, tiene más de dos decimales o es
	 *             demasiado grande
	 */
	public static BigDecimal validarPrecioTotal(BigDecimal precio) {
		if (precio == null) {
			throw new IllegalArgumentException("Captura el precio total de la venta");
		}
		if (precio.signum() <= 0) {
			throw new IllegalArgumentException("El precio total debe ser mayor a cero");
		}
		if (precio.stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException("El precio total admite hasta dos decimales");
		}
		if (precio.compareTo(PRECIO_MAXIMO) > 0) {
			throw new IllegalArgumentException("El precio total es demasiado grande");
		}
		return precio.setScale(2);
	}

	/**
	 * Guarda o corrige los datos fiscales del cliente (ERP-1.3). Solo en Pago: es
	 * cuando el vendedor los captura, y después los usa la factura.
	 *
	 * @throws ConflictoException
	 *             si el prospecto no está en Pago
	 */
	public void capturarDatosFiscales(DatosFiscales datos) {
		Objects.requireNonNull(datos, "datos");
		exigirEtapa(Etapa.PAGO, "Los datos fiscales se capturan mientras el prospecto está en Pago");
		this.datosFiscales = datos;
	}

	/**
	 * Guarda el correo del cliente al finalizar, si faltaba o venía mal (CRM-1.4:
	 * sin correo no se crea su cuenta de la app).
	 *
	 * @throws IllegalArgumentException
	 *             si viene vacío
	 */
	public void capturarCorreo(String correo) {
		String correoLimpio = normalizarCorreo(correo);
		if (correoLimpio == null) {
			throw new IllegalArgumentException("El correo del cliente está vacío");
		}
		this.correo = correoLimpio;
	}

	/**
	 * Pasa el prospecto a otro vendedor (CRM-0.1). Conserva la etapa y el
	 * historial; solo cambia quién lo atiende.
	 *
	 * @return {@code false} si el prospecto ya era de ese vendedor
	 */
	public boolean reasignarA(Long nuevoVendedorId) {
		Objects.requireNonNull(nuevoVendedorId, "nuevoVendedorId");
		if (nuevoVendedorId.equals(vendedorId)) {
			return false;
		}
		vendedorId = nuevoVendedorId;
		return true;
	}

	/**
	 * Lo usa {@link SolicitudCierre}: Pago, Facturación o Entrega sin otra
	 * pendiente.
	 */
	void marcarCierreSolicitado() {
		if (!etapa.estaEnProcesoDeVenta()) {
			throw new ConflictoException(
					"Solo se solicita el cierre desde Pago, Facturación o Entrega; antes lo cierra el vendedor");
		}
		if (cierrePendiente) {
			throw new ConflictoException("Este prospecto ya tiene una solicitud de cierre pendiente");
		}
		cierrePendiente = true;
	}

	/** Lo usa {@link SolicitudCierre} cuando el Administrador la aprueba. */
	void cerrarPorAutorizacion(MotivoCierre motivo, Long administradorId, Instant ahora) {
		cierrePendiente = false;
		cerrar(motivo, OrigenCambio.ERP, administradorId, ahora);
	}

	/** Lo usa {@link SolicitudCierre} cuando el Administrador la rechaza. */
	void descartarCierreSolicitado() {
		cierrePendiente = false;
	}

	private void cerrar(MotivoCierre motivo, OrigenCambio origen, Long usuarioId, Instant ahora) {
		this.motivoCierre = MotivoCierre.validarParaCerrar(motivo);
		this.ultimaEtapa = etapa;
		moverA(Etapa.CERRADO, origen, usuarioId, ahora);
	}

	private void exigirEtapa(Etapa esperada, String mensaje) {
		if (etapa != esperada) {
			throw new ConflictoException(mensaje);
		}
	}

	private String motivoDeBloqueo(Etapa nueva) {
		return switch (etapa) {
			case PAGO -> nueva == Etapa.CERRADO
					? "Para cerrar un prospecto en Pago envía una solicitud de cierre al Administrador"
					: "En Pago el prospecto avanza cuando Contabilidad valida el pago";
			case FACTURACION -> nueva == Etapa.CERRADO
					? "Para cerrar un prospecto en Facturación envía una solicitud de cierre al Administrador"
					: "En Facturación el prospecto avanza cuando Facturación emite la factura";
			case ENTREGA -> nueva == Etapa.CERRADO
					? "Para cerrar un prospecto en Entrega envía una solicitud de cierre al Administrador"
					: "Desde Entrega solo puedes finalizar el prospecto";
			case FINALIZADO -> "El prospecto ya está finalizado";
			case CERRADO -> "El prospecto ya está cerrado";
			default -> "Cambio de etapa no permitido";
		};
	}

	private void moverA(Etapa nueva, OrigenCambio origen, Long usuarioId, Instant ahora) {
		Objects.requireNonNull(origen, "origen");
		Objects.requireNonNull(ahora, "ahora");
		etapa = nueva;
		etapaActualizadaEn = ahora;
		historial.add(new HistorialEtapa(this, nueva, origen, usuarioId, ahora));
	}

	/**
	 * Deja el teléfono solo con sus 10 dígitos; acepta espacios, guiones, puntos y
	 * paréntesis.
	 *
	 * @return {@code null} si viene vacío
	 * @throws IllegalArgumentException
	 *             si no quedan exactamente 10 dígitos
	 */
	public static String normalizarTelefono(String telefono) {
		String limpio = limpiar(telefono);
		if (limpio == null) {
			return null;
		}
		String digitos = SEPARADORES_TELEFONO.matcher(limpio).replaceAll("");
		if (!DIEZ_DIGITOS.matcher(digitos).matches()) {
			throw new IllegalArgumentException("El teléfono debe tener 10 dígitos");
		}
		return digitos;
	}

	/**
	 * @return el correo sin espacios y en minúsculas, o {@code null} si viene vacío
	 */
	public static String normalizarCorreo(String correo) {
		String limpio = limpiar(correo);
		return limpio == null ? null : limpio.toLowerCase(Locale.ROOT);
	}

	private static String nombreDe(Etapa etapa) {
		return switch (etapa) {
			case PRIMER_CONTACTO -> "Primer contacto";
			case VISITA -> "Visita";
			case COTIZACION -> "Cotización";
			default -> etapa.name();
		};
	}

	private static String limpiar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.strip();
	}

	public Long getId() {
		return id;
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

	public Etapa getEtapa() {
		return etapa;
	}

	public Long getVendedorId() {
		return vendedorId;
	}

	public MotivoCierre getMotivoCierre() {
		return motivoCierre;
	}

	public Etapa getUltimaEtapa() {
		return ultimaEtapa;
	}

	public Instant getEtapaActualizadaEn() {
		return etapaActualizadaEn;
	}

	public BigDecimal getPrecioTotal() {
		return precioTotal;
	}

	public DatosFiscales getDatosFiscales() {
		return datosFiscales;
	}

	public boolean isCierrePendiente() {
		return cierrePendiente;
	}

	public List<HistorialEtapa> getHistorial() {
		return Collections.unmodifiableList(historial);
	}
}
