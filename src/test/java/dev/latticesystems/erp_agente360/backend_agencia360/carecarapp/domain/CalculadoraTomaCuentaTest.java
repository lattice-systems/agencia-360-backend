package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CalculadoraTomaCuenta.Resultado;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * La fórmula de la toma a cuenta (V2-M03.06) con números exactos. Parámetro de
 * los ejemplos: valor base 300,000, depreciación 10 %, factor de kilometraje
 * 0.05 y de estado 0.10. Un auto de 2021 estimado en 2026 tiene 5 años: base de
 * 300,000 * 0.9^5 = 177,147 y 75,000 km esperados.
 */
class CalculadoraTomaCuentaTest {

	private static final int ANIO_ACTUAL = 2026;

	private final CalculadoraTomaCuenta calculadora = new CalculadoraTomaCuenta();
	private final ParametroTomaCuenta parametro = parametro("300000");

	private static ParametroTomaCuenta parametro(String valorBase) {
		return ParametroTomaCuenta.nuevo("Nissan", "Versa", 2021, new BigDecimal(valorBase), new BigDecimal("0.10"),
				new BigDecimal("0.05"), new BigDecimal("0.10"));
	}

	private Resultado calcular(int anioAuto, int km, EstadoVehiculo estado) {
		return calculadora.calcular(parametro, ANIO_ACTUAL, anioAuto, km, estado);
	}

	private static void assertRango(Resultado resultado, String minimo, String maximo) {
		assertThat(resultado.minimo()).isEqualByComparingTo(minimo);
		assertThat(resultado.maximo()).isEqualByComparingTo(maximo);
	}

	@Test
	void unAutoConLosKilometrosEsperadosYEstadoBuenoSoloDepreciaPorEdad() {
		Resultado resultado = calcular(2021, 75_000, EstadoVehiculo.BUENO);

		assertThat(resultado.edadAnios()).isEqualTo(5);
		assertThat(resultado.kmEsperado()).isEqualTo(75_000);
		assertThat(resultado.valorBase()).isEqualByComparingTo("300000");
		assertThat(resultado.ajusteKm()).isEqualByComparingTo("1");
		assertThat(resultado.ajusteEstado()).isEqualByComparingTo("1");
		assertThat(resultado.margen()).isEqualByComparingTo("0.08");
		// 177,147 * 0.92 = 162,975.24 y 177,147 * 1.08 = 191,318.76
		assertRango(resultado, "163000", "191300");
	}

	@Test
	void unAutoDelAnioNoTieneEdadNiKilometrosEsperados() {
		Resultado resultado = calcular(2026, 0, EstadoVehiculo.BUENO);

		assertThat(resultado.edadAnios()).isZero();
		assertThat(resultado.kmEsperado()).isZero();
		assertRango(resultado, "276000", "324000");
	}

	@Test
	void unAutoDeUnAnioPosteriorCuentaConEdadCero() {
		Resultado resultado = calcular(2027, 0, EstadoVehiculo.BUENO);

		assertThat(resultado.edadAnios()).isZero();
		assertRango(resultado, "276000", "324000");
	}

	@Test
	void elDobleDeKilometrosBajaElValor() {
		Resultado resultado = calcular(2021, 150_000, EstadoVehiculo.BUENO);

		// 1 - 0.05 * 75,000 / 10,000 = 0.625; 177,147 * 0.625 = 110,716.875
		assertThat(resultado.ajusteKm()).isEqualByComparingTo("0.625");
		assertRango(resultado, "101900", "119600");
	}

	@Test
	void cuantoMenosKilometrosMasSubeElAjusteHastaElLimiteDe1Punto2() {
		Resultado conMenos = calcular(2021, 60_000, EstadoVehiculo.BUENO);
		Resultado conCero = calcular(2021, 0, EstadoVehiculo.BUENO);

		// 1 - 0.05 * (-15,000) / 10,000 = 1.075
		assertThat(conMenos.ajusteKm()).isEqualByComparingTo("1.075");
		// Con 0 km saldría 1.375: se limita a 1.2. 177,147 * 1.2 = 212,576.4
		assertThat(conCero.ajusteKm()).isEqualByComparingTo("1.2");
		assertRango(conCero, "195600", "229600");
	}

	@Test
	void elAjusteDeKilometrosNuncaBajaDe0Punto5() {
		Resultado resultado = calcular(2021, 999_999, EstadoVehiculo.BUENO);

		assertThat(resultado.ajusteKm()).isEqualByComparingTo("0.5");
		// 177,147 * 0.5 = 88,573.5
		assertRango(resultado, "81500", "95700");
	}

	@Test
	void cadaEstadoMueveElValorUnPasoDelFactor() {
		assertThat(calcular(2021, 75_000, EstadoVehiculo.EXCELENTE).ajusteEstado()).isEqualByComparingTo("1.1");
		assertThat(calcular(2021, 75_000, EstadoVehiculo.BUENO).ajusteEstado()).isEqualByComparingTo("1");
		assertThat(calcular(2021, 75_000, EstadoVehiculo.REGULAR).ajusteEstado()).isEqualByComparingTo("0.9");
		assertThat(calcular(2021, 75_000, EstadoVehiculo.MALO).ajusteEstado()).isEqualByComparingTo("0.8");
		// 177,147 * 1.1 = 194,861.7 y 177,147 * 0.8 = 141,717.6
		assertRango(calcular(2021, 75_000, EstadoVehiculo.EXCELENTE), "179300", "210500");
		assertRango(calcular(2021, 75_000, EstadoVehiculo.MALO), "130400", "153100");
	}

	@Test
	void unMejorEstadoNuncaValeMenosQueUnoPeor() {
		BigDecimal anterior = null;
		for (EstadoVehiculo estado : new EstadoVehiculo[]{EstadoVehiculo.MALO, EstadoVehiculo.REGULAR,
				EstadoVehiculo.BUENO, EstadoVehiculo.EXCELENTE}) {
			BigDecimal minimo = calcular(2021, 75_000, estado).minimo();
			if (anterior != null) {
				assertThat(minimo).isGreaterThan(anterior);
			}
			anterior = minimo;
		}
	}

	@Test
	void elRangoSeRedondeaALaCentenaMasCercana() {
		Resultado resultado = calcular(2021, 75_000, EstadoVehiculo.BUENO);

		assertThat(resultado.minimo().remainder(BigDecimal.valueOf(100))).isEqualByComparingTo("0");
		assertThat(resultado.maximo().remainder(BigDecimal.valueOf(100))).isEqualByComparingTo("0");
		assertThat(resultado.minimo()).isLessThanOrEqualTo(resultado.maximo());
	}

	@Test
	void unValorBaseDeCeroDaUnRangoDeCeroNoNegativo() {
		Resultado resultado = calculadora.calcular(parametro("0"), ANIO_ACTUAL, 2021, 75_000, EstadoVehiculo.MALO);

		assertRango(resultado, "0", "0");
	}

	@Test
	void elMargenYLosKilometrosPorAnioSeAjustan() {
		CalculadoraTomaCuenta propia = new CalculadoraTomaCuenta(10_000, new BigDecimal("0.10"));

		Resultado resultado = propia.calcular(parametro, ANIO_ACTUAL, 2021, 50_000, EstadoVehiculo.BUENO);

		assertThat(resultado.kmEsperado()).isEqualTo(50_000);
		assertThat(resultado.margen()).isEqualByComparingTo("0.10");
		// 177,147 * 0.90 = 159,432.3 y 177,147 * 1.10 = 194,861.7
		assertRango(resultado, "159400", "194900");
	}

	@Test
	void noAceptaKilometrajeNegativo() {
		assertThatThrownBy(() -> calcular(2021, -1, EstadoVehiculo.BUENO)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void noAceptaConstantesFueraDeRango() {
		assertThatThrownBy(() -> new CalculadoraTomaCuenta(-1, new BigDecimal("0.08")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CalculadoraTomaCuenta(15_000, new BigDecimal("1.5")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CalculadoraTomaCuenta(15_000, new BigDecimal("-0.01")))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
