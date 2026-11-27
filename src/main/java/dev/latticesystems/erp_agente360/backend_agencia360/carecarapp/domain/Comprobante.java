package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Lo que se timbra en la factura de un pago de la app (V2-M08.06), con la
 * estructura de un CFDI 4.0 de ingreso: emisor, receptor, conceptos con su IVA,
 * forma y método de pago. Los precios del ticket y de la tienda traen el IVA
 * incluido, así que cada concepto se desglosa en su base y su IVA y la suma de
 * los conceptos es el total pagado.
 *
 * @param fecha
 *            la fecha de emisión en la hora de la agencia, sin zona, como la
 *            pide el CFDI
 * @param formaPago
 *            clave del SAT: {@code 04} tarjeta de crédito (y las billeteras) o
 *            {@code 28} tarjeta de débito
 */
public record Comprobante(String serie, long folio, LocalDateTime fecha, Emisor emisor, Receptor receptor,
		List<Concepto> conceptos, String formaPago) {

	public static final String VERSION = "4.0";
	public static final String TIPO_INGRESO = "I";
	public static final String METODO_PAGO = "PUE";
	public static final String MONEDA = "MXN";
	public static final String EXPORTACION_NO_APLICA = "01";
	public static final String FORMA_CREDITO = "04";
	public static final String FORMA_DEBITO = "28";

	/** Mantenimiento y reparación de vehículos, por unidad de servicio. */
	static final String CLAVE_MANO_DE_OBRA = "78181500";
	static final String UNIDAD_SERVICIO = "E48";

	/** Componentes especializados de vehículos, por pieza. */
	static final String CLAVE_REFACCION = "25174800";
	static final String UNIDAD_PIEZA = "H87";

	public Comprobante {
		Objects.requireNonNull(serie, "serie");
		Objects.requireNonNull(fecha, "fecha");
		Objects.requireNonNull(emisor, "emisor");
		Objects.requireNonNull(receptor, "receptor");
		Objects.requireNonNull(formaPago, "formaPago");
		if (conceptos == null || conceptos.isEmpty()) {
			throw new IllegalArgumentException("Una factura lleva al menos un concepto");
		}
		conceptos = List.copyOf(conceptos);
	}

	/** La serie y el folio como se muestran: {@code A-000124}. */
	public String folioCompleto() {
		return "%s-%06d".formatted(serie, folio);
	}

	/** El folio sin la serie, como va en el atributo {@code Folio} del XML. */
	public String folioSinSerie() {
		return "%06d".formatted(folio);
	}

	/** La suma de las bases, sin IVA. */
	public BigDecimal subtotal() {
		return conceptos.stream().map(Concepto::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** La suma del IVA trasladado de cada concepto. */
	public BigDecimal iva() {
		return conceptos.stream().map(Concepto::iva).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public BigDecimal total() {
		return subtotal().add(iva());
	}

	/**
	 * Quien factura: la agencia, con el RFC de prueba del SAT mientras el timbrado
	 * sea simulado (ADR 0009).
	 */
	public record Emisor(String rfc, String nombre, String regimenFiscal, String codigoPostal) {

		public Emisor {
			Objects.requireNonNull(rfc, "rfc");
			Objects.requireNonNull(nombre, "nombre");
			Objects.requireNonNull(regimenFiscal, "regimenFiscal");
			Objects.requireNonNull(codigoPostal, "codigoPostal");
		}
	}

	/**
	 * La copia del perfil fiscal del cliente con que se timbra: si después cambia o
	 * se borra el perfil, la factura no cambia.
	 */
	public record Receptor(String rfc, String nombre, String codigoPostal, String regimenFiscal, String usoCfdi) {

		public Receptor {
			Objects.requireNonNull(rfc, "rfc");
			Objects.requireNonNull(nombre, "nombre");
			Objects.requireNonNull(codigoPostal, "codigoPostal");
			Objects.requireNonNull(regimenFiscal, "regimenFiscal");
			Objects.requireNonNull(usoCfdi, "usoCfdi");
		}

		/** Persona moral: su RFC tiene 12 caracteres. */
		public boolean esMoral() {
			return rfc.length() == 12;
		}
	}

	/**
	 * Un renglón de la factura, ya sin IVA.
	 *
	 * @param valorUnitario
	 *            la base entre la cantidad, a 6 decimales como admite el CFDI
	 * @param importe
	 *            la base del concepto, a 2 decimales
	 * @param iva
	 *            el IVA trasladado del concepto, a 2 decimales
	 */
	public record Concepto(String claveProdServ, String claveUnidad, String unidad, String descripcion, int cantidad,
			BigDecimal valorUnitario, BigDecimal importe, BigDecimal iva) {

		/** IVA del 16 %, como lo pide el CFDI en {@code TasaOCuota}. */
		public static final BigDecimal TASA_IVA = new BigDecimal("0.160000");

		private static final BigDecimal UNO_MAS_IVA = new BigDecimal("1.16");

		public Concepto {
			Objects.requireNonNull(claveProdServ, "claveProdServ");
			Objects.requireNonNull(claveUnidad, "claveUnidad");
			Objects.requireNonNull(descripcion, "descripcion");
			if (cantidad <= 0) {
				throw new IllegalArgumentException("La cantidad de un concepto es mayor que cero");
			}
			Objects.requireNonNull(valorUnitario, "valorUnitario");
			Objects.requireNonNull(importe, "importe");
			Objects.requireNonNull(iva, "iva");
		}

		/** Mano de obra del ticket: clave 78181500 y unidad de servicio E48. */
		public static Concepto manoDeObra(String descripcion, int cantidad, BigDecimal importeConIva) {
			return conIvaIncluido(CLAVE_MANO_DE_OBRA, UNIDAD_SERVICIO, "Unidad de servicio", descripcion, cantidad,
					importeConIva);
		}

		/** Una refacción del ticket o de la tienda: clave 25174800 y pieza H87. */
		public static Concepto refaccion(String descripcion, int cantidad, BigDecimal importeConIva) {
			return conIvaIncluido(CLAVE_REFACCION, UNIDAD_PIEZA, "Pieza", descripcion, cantidad, importeConIva);
		}

		/**
		 * Desglosa un importe con IVA: la base es el importe entre 1.16 y el IVA es la
		 * diferencia, así que base más IVA da exactamente el importe.
		 */
		static Concepto conIvaIncluido(String clave, String claveUnidad, String unidad, String descripcion,
				int cantidad, BigDecimal importeConIva) {
			if (importeConIva == null || importeConIva.signum() <= 0) {
				throw new IllegalArgumentException("El importe de un concepto es mayor que cero");
			}
			if (cantidad <= 0) {
				throw new IllegalArgumentException("La cantidad de un concepto es mayor que cero");
			}
			BigDecimal conIva = importeConIva.setScale(2, RoundingMode.HALF_UP);
			BigDecimal base = conIva.divide(UNO_MAS_IVA, 2, RoundingMode.HALF_UP);
			BigDecimal valorUnitario = base.divide(BigDecimal.valueOf(cantidad), 6, RoundingMode.HALF_UP);
			return new Concepto(clave, claveUnidad, unidad, descripcion, cantidad, valorUnitario, base,
					conIva.subtract(base));
		}

		/** Lo que el cliente pagó por el concepto: base más IVA. */
		public BigDecimal importeConIva() {
			return importe.add(iva);
		}
	}
}
