package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Lo que la agencia sabe de un auto para valuarlo a cuenta (V2-M03.06): su
 * valor de referencia y los factores de la fórmula. Como en el inventario,
 * {@code modelo} es el Tipo (Versa) y {@code anio} el Modelo (2021). Un
 * parámetro que deja de usarse se desactiva, no se borra, porque las
 * estimaciones guardadas lo citan.
 */
@Entity
@Table(name = "parametro_toma_cuenta")
public class ParametroTomaCuenta extends Auditable {

	private static final BigDecimal MAXIMO_DEPRECIACION = new BigDecimal("0.5");
	private static final BigDecimal MAXIMO_FACTOR_KM = new BigDecimal("0.2");
	private static final BigDecimal MAXIMO_FACTOR_ESTADO = new BigDecimal("0.3");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40)
	private String marca;

	@Column(nullable = false, length = 80)
	private String modelo;

	@Column(nullable = false)
	private int anio;

	@Column(name = "valor_base", nullable = false, precision = 12, scale = 2)
	private BigDecimal valorBase;

	@Column(name = "depreciacion_anual", nullable = false, precision = 5, scale = 4)
	private BigDecimal depreciacionAnual;

	@Column(name = "factor_km", nullable = false, precision = 5, scale = 4)
	private BigDecimal factorKm;

	@Column(name = "factor_estado", nullable = false, precision = 5, scale = 4)
	private BigDecimal factorEstado;

	@Column(nullable = false)
	private boolean activo;

	protected ParametroTomaCuenta() {
		// Requerido por JPA
	}

	/**
	 * Un parámetro activo.
	 *
	 * @throws IllegalArgumentException
	 *             si falta la marca o el modelo, el año es menor a 1900, el valor
	 *             es negativo o un factor sale de su rango: depreciación de 0 a
	 *             0.5, kilometraje de 0 a 0.2 y estado de 0 a 0.3 (400)
	 */
	public static ParametroTomaCuenta nuevo(String marca, String modelo, int anio, BigDecimal valorBase,
			BigDecimal depreciacionAnual, BigDecimal factorKm, BigDecimal factorEstado) {
		ParametroTomaCuenta nuevo = new ParametroTomaCuenta();
		nuevo.marca = obligatorio(marca, "La marca", 40);
		nuevo.modelo = obligatorio(modelo, "El modelo", 80);
		if (anio < 1900) {
			throw new IllegalArgumentException("El año no es válido");
		}
		nuevo.anio = anio;
		if (valorBase == null || valorBase.signum() < 0) {
			throw new IllegalArgumentException("El valor base no puede ser negativo");
		}
		nuevo.valorBase = valorBase;
		nuevo.depreciacionAnual = enRango(depreciacionAnual, MAXIMO_DEPRECIACION, "La depreciación anual");
		nuevo.factorKm = enRango(factorKm, MAXIMO_FACTOR_KM, "El factor de kilometraje");
		nuevo.factorEstado = enRango(factorEstado, MAXIMO_FACTOR_ESTADO, "El factor de estado");
		nuevo.activo = true;
		return nuevo;
	}

	/** Deja de usarse en estimaciones nuevas. Las guardadas lo siguen citando. */
	public void desactivar() {
		this.activo = false;
	}

	private static BigDecimal enRango(BigDecimal valor, BigDecimal maximo, String nombre) {
		if (valor == null || valor.signum() < 0 || valor.compareTo(maximo) > 0) {
			throw new IllegalArgumentException(nombre + " debe estar entre 0 y " + maximo.toPlainString());
		}
		return valor;
	}

	private static String obligatorio(String valor, String nombre, int largo) {
		if (valor == null || valor.isBlank()) {
			throw new IllegalArgumentException(nombre + " es obligatoria");
		}
		String limpio = valor.strip();
		if (limpio.length() > largo) {
			throw new IllegalArgumentException(nombre + " no puede pasar de " + largo + " caracteres");
		}
		return limpio;
	}

	public Long getId() {
		return id;
	}

	public String getMarca() {
		return marca;
	}

	public String getModelo() {
		return modelo;
	}

	public int getAnio() {
		return anio;
	}

	public BigDecimal getValorBase() {
		return valorBase;
	}

	public BigDecimal getDepreciacionAnual() {
		return depreciacionAnual;
	}

	public BigDecimal getFactorKm() {
		return factorKm;
	}

	public BigDecimal getFactorEstado() {
		return factorEstado;
	}

	public boolean isActivo() {
		return activo;
	}
}
