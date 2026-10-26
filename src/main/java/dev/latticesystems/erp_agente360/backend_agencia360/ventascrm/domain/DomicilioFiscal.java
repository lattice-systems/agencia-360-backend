package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;

/**
 * Domicilio fiscal del cliente, separado como lo pidió el PO el 26 de octubre:
 * estado, ciudad, colonia, calle y número, y código postal (la factura 4.0 pide
 * el código postal del receptor).
 */
@Embeddable
public class DomicilioFiscal {

	private static final Pattern CODIGO_POSTAL = Pattern.compile("\\d{5}");
	private static final int LARGO_ESTADO = 40;
	private static final int LARGO_CIUDAD = 60;
	private static final int LARGO_COLONIA = 80;
	private static final int LARGO_CALLE_NUMERO = 90;

	@Column(name = "fiscal_estado", length = LARGO_ESTADO)
	private String estado;

	@Column(name = "fiscal_ciudad", length = LARGO_CIUDAD)
	private String ciudad;

	@Column(name = "fiscal_colonia", length = LARGO_COLONIA)
	private String colonia;

	@Column(name = "fiscal_calle_numero", length = LARGO_CALLE_NUMERO)
	private String calleNumero;

	@Column(name = "fiscal_codigo_postal", length = 5)
	private String codigoPostal;

	protected DomicilioFiscal() {
		// Requerido por JPA
	}

	private DomicilioFiscal(String estado, String ciudad, String colonia, String calleNumero, String codigoPostal) {
		this.estado = estado;
		this.ciudad = ciudad;
		this.colonia = colonia;
		this.calleNumero = calleNumero;
		this.codigoPostal = codigoPostal;
	}

	/**
	 * Quita espacios sobrantes.
	 *
	 * @throws IllegalArgumentException
	 *             si falta algún dato, alguno es demasiado largo o el código postal
	 *             no son 5 dígitos
	 */
	public static DomicilioFiscal de(String estado, String ciudad, String colonia, String calleNumero,
			String codigoPostal) {
		String codigoPostalLimpio = exigir(codigoPostal, "el código postal", 5);
		if (!CODIGO_POSTAL.matcher(codigoPostalLimpio).matches()) {
			throw new IllegalArgumentException("El código postal son 5 dígitos");
		}
		return new DomicilioFiscal(exigir(estado, "el estado", LARGO_ESTADO), exigir(ciudad, "la ciudad", LARGO_CIUDAD),
				exigir(colonia, "la colonia", LARGO_COLONIA),
				exigir(calleNumero, "la calle y el número", LARGO_CALLE_NUMERO), codigoPostalLimpio);
	}

	private static String exigir(String valor, String dato, int largoMaximo) {
		if (valor == null || valor.isBlank()) {
			throw new IllegalArgumentException("Captura " + dato + " del domicilio fiscal");
		}
		String limpio = valor.strip();
		if (limpio.length() > largoMaximo) {
			throw new IllegalArgumentException(
					"En el domicilio fiscal, " + dato + " admite hasta " + largoMaximo + " caracteres");
		}
		return limpio;
	}

	/**
	 * @return el domicilio en una línea, como va en la factura: "Blvd. López Mateos
	 *         1102, Col. Centro, C.P. 37000, León, Guanajuato"
	 */
	public String enUnaLinea() {
		return calleNumero + ", Col. " + colonia + ", C.P. " + codigoPostal + ", " + ciudad + ", " + estado;
	}

	public String getEstado() {
		return estado;
	}

	public String getCiudad() {
		return ciudad;
	}

	public String getColonia() {
		return colonia;
	}

	public String getCalleNumero() {
		return calleNumero;
	}

	public String getCodigoPostal() {
		return codigoPostal;
	}
}
