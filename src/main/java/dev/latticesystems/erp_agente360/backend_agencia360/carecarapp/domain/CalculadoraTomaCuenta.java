package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * La fórmula de la toma a cuenta (V2-M03.06). Vive solo aquí: la app muestra el
 * resultado y nunca lo recalcula. Sin dependencias de Spring ni de la base para
 * probarla con números exactos.
 *
 * <p>
 * Con el parámetro de la marca, modelo y año del auto:
 * <ol>
 * <li>{@code edad = max(0, anioActual - anio)}</li>
 * <li>{@code base = valorBase * (1 - depreciacionAnual) ^ edad}</li>
 * <li>{@code kmEsperado = edad * kmAnuales}</li>
 * <li>{@code ajusteKm = 1 - factorKm * (km - kmEsperado) / 10000}, entre 0.5 y
 * 1.2: menos kilómetros de los esperados lo sube y más lo baja</li>
 * <li>{@code ajusteEstado = 1 - factorEstado * pasos}, con los pasos de
 * {@link EstadoVehiculo}</li>
 * <li>{@code estimado = base * ajusteKm * ajusteEstado}</li>
 * <li>{@code minimo = estimado * (1 - margen)} y
 * {@code maximo = estimado * (1 + margen)}, redondeados a la centena más
 * cercana y nunca menores a 0</li>
 * </ol>
 */
public final class CalculadoraTomaCuenta {

	/** Kilómetros que se espera recorra un auto por año. */
	public static final int KM_ANUALES_POR_OMISION = 15_000;

	/** Fracción que se resta y se suma al valor estimado (8 %). */
	public static final BigDecimal MARGEN_POR_OMISION = new BigDecimal("0.08");

	static final BigDecimal AJUSTE_KM_MINIMO = new BigDecimal("0.5");
	static final BigDecimal AJUSTE_KM_MAXIMO = new BigDecimal("1.2");

	private static final BigDecimal KM_POR_PASO = BigDecimal.valueOf(10_000);
	private static final BigDecimal CIEN = BigDecimal.valueOf(100);
	private static final int ESCALA_AJUSTES = 4;

	private final int kmAnuales;
	private final BigDecimal margen;

	/**
	 * @throws IllegalArgumentException
	 *             si los kilómetros por año son negativos o el margen no está entre
	 *             0 y 1
	 */
	public CalculadoraTomaCuenta(int kmAnuales, BigDecimal margen) {
		if (kmAnuales < 0) {
			throw new IllegalArgumentException("Los kilómetros por año no pueden ser negativos");
		}
		if (margen == null || margen.signum() < 0 || margen.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("El margen debe estar entre 0 y 1");
		}
		this.kmAnuales = kmAnuales;
		this.margen = margen;
	}

	/** Con los valores por omisión: 15,000 km por año y margen de 8 %. */
	public CalculadoraTomaCuenta() {
		this(KM_ANUALES_POR_OMISION, MARGEN_POR_OMISION);
	}

	/**
	 * Lo que valdría el auto, como rango.
	 *
	 * @param anioActual
	 *            año en que se estima; un auto de un año posterior cuenta con edad
	 *            0
	 * @param anioAuto
	 *            año modelo del auto
	 * @param km
	 *            kilometraje actual, no negativo
	 */
	public Resultado calcular(ParametroTomaCuenta parametro, int anioActual, int anioAuto, int km,
			EstadoVehiculo estado) {
		if (km < 0) {
			throw new IllegalArgumentException("El kilometraje no puede ser negativo");
		}
		int edad = Math.max(0, anioActual - anioAuto);
		BigDecimal conservado = BigDecimal.ONE.subtract(parametro.getDepreciacionAnual());
		BigDecimal base = parametro.getValorBase().multiply(conservado.pow(edad));

		long kmEsperado = (long) edad * kmAnuales;
		BigDecimal exceso = BigDecimal.valueOf(km - kmEsperado);
		BigDecimal ajusteKm = BigDecimal.ONE
				.subtract(parametro.getFactorKm().multiply(exceso).divide(KM_POR_PASO, 10, RoundingMode.HALF_UP))
				.max(AJUSTE_KM_MINIMO).min(AJUSTE_KM_MAXIMO).setScale(ESCALA_AJUSTES, RoundingMode.HALF_UP);
		BigDecimal ajusteEstado = BigDecimal.ONE
				.subtract(parametro.getFactorEstado().multiply(BigDecimal.valueOf(estado.pasos())))
				.setScale(ESCALA_AJUSTES, RoundingMode.HALF_UP);

		BigDecimal estimado = base.multiply(ajusteKm).multiply(ajusteEstado);
		BigDecimal minimo = aCentenas(estimado.multiply(BigDecimal.ONE.subtract(margen)));
		BigDecimal maximo = aCentenas(estimado.multiply(BigDecimal.ONE.add(margen)));
		return new Resultado(parametro.getValorBase(), edad, kmEsperado, ajusteKm, ajusteEstado, margen, minimo,
				maximo);
	}

	/** A la centena más cercana, sin bajar de 0. */
	private static BigDecimal aCentenas(BigDecimal valor) {
		BigDecimal centenas = valor.divide(CIEN, 0, RoundingMode.HALF_UP).multiply(CIEN);
		return centenas.signum() < 0 ? BigDecimal.ZERO.setScale(2) : centenas.setScale(2);
	}

	/**
	 * El rango y los supuestos con que salió, para mostrarlos al cliente y auditar
	 * el resultado.
	 */
	public record Resultado(BigDecimal valorBase, int edadAnios, long kmEsperado, BigDecimal ajusteKm,
			BigDecimal ajusteEstado, BigDecimal margen, BigDecimal minimo, BigDecimal maximo) {
	}
}
