package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Gasto de un vehículo en la bitácora del conductor (V2-M18.01 y V2-M18.04). La
 * gasolina lleva litros y kilometraje; "otro" lleva descripción. La carga de
 * gasolina guarda la lectura de km que creó ({@code lecturaKmId}).
 */
@Entity
@Table(name = "gasto_vehiculo")
public class GastoVehiculo {

	public static final int DESCRIPCION_MAXIMA = 200;
	private static final BigDecimal MONTO_MAXIMO = new BigDecimal("99999999.99");
	private static final BigDecimal LITROS_MAXIMOS = new BigDecimal("999999.99");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehiculo_cliente_id", nullable = false, updatable = false)
	private Long vehiculoClienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private TipoGasto tipo;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(precision = 8, scale = 2)
	private BigDecimal litros;

	private Integer km;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(length = DESCRIPCION_MAXIMA)
	private String descripcion;

	@Column(name = "lectura_km_id")
	private Long lecturaKmId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GastoVehiculo() {
		// Requerido por JPA
	}

	/**
	 * @param hoy
	 *            el día de hoy en la agencia; la fecha del gasto no puede pasar de
	 *            él
	 * @throws IllegalArgumentException
	 *             si falta un dato o no cumple las reglas del tipo (400)
	 */
	public static GastoVehiculo registrar(Long vehiculoClienteId, TipoGasto tipo, BigDecimal monto, BigDecimal litros,
			Integer km, LocalDate fecha, String descripcion, LocalDate hoy, Instant ahora) {
		Objects.requireNonNull(vehiculoClienteId, "vehiculoClienteId");
		GastoVehiculo gasto = new GastoVehiculo();
		gasto.vehiculoClienteId = vehiculoClienteId;
		gasto.createdAt = ahora;
		gasto.reemplazar(tipo, monto, litros, km, fecha, descripcion, hoy, ahora);
		return gasto;
	}

	/**
	 * Cambia todos los datos del gasto con las mismas reglas que al registrarlo.
	 * Los litros y el km solo se guardan en la gasolina.
	 */
	public void reemplazar(TipoGasto nuevoTipo, BigDecimal nuevoMonto, BigDecimal nuevosLitros, Integer nuevoKm,
			LocalDate nuevaFecha, String nuevaDescripcion, LocalDate hoy, Instant ahora) {
		if (nuevoTipo == null) {
			throw new IllegalArgumentException("El tipo de gasto es obligatorio");
		}
		if (nuevoMonto == null || nuevoMonto.signum() <= 0 || nuevoMonto.compareTo(MONTO_MAXIMO) > 0) {
			throw new IllegalArgumentException("El monto debe ser mayor a 0");
		}
		if (nuevaFecha == null || nuevaFecha.isAfter(hoy)) {
			throw new IllegalArgumentException("La fecha es obligatoria y no puede ser futura");
		}
		String limpia = nuevaDescripcion == null || nuevaDescripcion.isBlank() ? null : nuevaDescripcion.strip();
		if (limpia != null && limpia.length() > DESCRIPCION_MAXIMA) {
			throw new IllegalArgumentException("La descripción pasa de " + DESCRIPCION_MAXIMA + " caracteres");
		}
		if (nuevoTipo == TipoGasto.OTRO && limpia == null) {
			throw new IllegalArgumentException("La descripción es obligatoria en un gasto de tipo otro");
		}
		if (nuevoTipo == TipoGasto.GASOLINA) {
			if (nuevosLitros == null || nuevosLitros.signum() <= 0 || nuevosLitros.compareTo(LITROS_MAXIMOS) > 0) {
				throw new IllegalArgumentException("Los litros deben ser mayores a 0");
			}
			if (nuevoKm == null) {
				throw new IllegalArgumentException("El kilometraje es obligatorio en la gasolina");
			}
		}
		if (nuevoKm != null) {
			LecturaKm.exigirKmValido(nuevoKm);
		}
		this.tipo = nuevoTipo;
		this.monto = nuevoMonto;
		this.litros = nuevoTipo == TipoGasto.GASOLINA ? nuevosLitros : null;
		this.km = nuevoKm;
		this.fecha = nuevaFecha;
		this.descripcion = limpia;
		this.updatedAt = ahora;
	}

	public void ligarLectura(Long lectura) {
		this.lecturaKmId = lectura;
	}

	public Long getId() {
		return id;
	}

	public Long getVehiculoClienteId() {
		return vehiculoClienteId;
	}

	public TipoGasto getTipo() {
		return tipo;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public BigDecimal getLitros() {
		return litros;
	}

	public Integer getKm() {
		return km;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public Long getLecturaKmId() {
		return lecturaKmId;
	}
}
