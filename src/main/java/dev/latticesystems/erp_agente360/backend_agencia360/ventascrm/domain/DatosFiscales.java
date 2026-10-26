package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Datos fiscales del cliente que recibe la factura (ERP-1.3, decisión del PO
 * del 21 de octubre): RFC, nombre completo, domicilio fiscal
 * ({@link DomicilioFiscal}) y régimen fiscal. Los captura el vendedor al pasar
 * el prospecto a Pago y sin ellos no llega a Facturación.
 *
 * <p>
 * Solo se valida el formato del RFC (persona física de 13 caracteres o moral de
 * 12, con una fecha válida); que exista ante el SAT no se revisa porque la
 * factura no se timbra.
 */
@Embeddable
public class DatosFiscales {

	private static final Pattern RFC = Pattern
			.compile("[A-ZÑ&]{3,4}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[A-Z\\d]{3}");
	private static final int LARGO_MAXIMO_NOMBRE = 200;

	@Column(name = "rfc", length = 13)
	private String rfc;

	@Column(name = "nombre_fiscal", length = LARGO_MAXIMO_NOMBRE)
	private String nombre;

	@Embedded
	private DomicilioFiscal domicilio;

	/** Clave del catálogo del SAT ({@link RegimenFiscal#getClave()}). */
	@Column(name = "regimen_fiscal", length = 3)
	private String regimen;

	protected DatosFiscales() {
		// Requerido por JPA
	}

	private DatosFiscales(String rfc, String nombre, DomicilioFiscal domicilio, String regimen) {
		this.rfc = rfc;
		this.nombre = nombre;
		this.domicilio = domicilio;
		this.regimen = regimen;
	}

	/**
	 * Quita espacios sobrantes y guarda el RFC en mayúsculas.
	 *
	 * @param regimen
	 *            clave del régimen en el catálogo del SAT, por ejemplo "612"
	 * @throws IllegalArgumentException
	 *             si falta algún dato, el RFC no tiene formato válido, el régimen
	 *             no está en el catálogo, el nombre es demasiado largo o falta el
	 *             domicilio
	 */
	public static DatosFiscales de(String rfc, String nombre, DomicilioFiscal domicilio, String regimen) {
		String rfcLimpio = limpiar(rfc);
		String nombreLimpio = limpiar(nombre);
		String regimenLimpio = limpiar(regimen);
		if (rfcLimpio == null) {
			throw new IllegalArgumentException("Captura el RFC del cliente");
		}
		rfcLimpio = rfcLimpio.toUpperCase(Locale.ROOT);
		if (!RFC.matcher(rfcLimpio).matches()) {
			throw new IllegalArgumentException("El RFC no tiene un formato válido");
		}
		if (nombreLimpio == null) {
			throw new IllegalArgumentException("Captura el nombre completo del cliente como aparece en su constancia");
		}
		if (nombreLimpio.length() > LARGO_MAXIMO_NOMBRE) {
			throw new IllegalArgumentException("El nombre fiscal admite hasta 200 caracteres");
		}
		if (domicilio == null) {
			throw new IllegalArgumentException("Captura el domicilio fiscal del cliente");
		}
		if (regimenLimpio == null) {
			throw new IllegalArgumentException("Elige el régimen fiscal del cliente");
		}
		return new DatosFiscales(rfcLimpio, nombreLimpio, domicilio,
				RegimenFiscal.desdeClave(regimenLimpio).getClave());
	}

	private static String limpiar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.strip();
	}

	public String getRfc() {
		return rfc;
	}

	public String getNombre() {
		return nombre;
	}

	public DomicilioFiscal getDomicilio() {
		return domicilio;
	}

	public RegimenFiscal getRegimen() {
		return RegimenFiscal.desdeClave(regimen);
	}
}
