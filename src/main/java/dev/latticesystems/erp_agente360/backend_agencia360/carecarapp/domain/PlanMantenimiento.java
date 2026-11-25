package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Un servicio del plan de mantenimiento de un modelo (V2-M05.01). Como en el
 * inventario, {@code modelo} es el Tipo (March) y aplica a los vehículos cuyo
 * año esté entre {@code anioDesde} y {@code anioHasta}. Los datos los siembra
 * la agencia; la app solo los lee.
 */
@Entity
@Table(name = "plan_mantenimiento")
public class PlanMantenimiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String modelo;

	@Column(name = "anio_desde", nullable = false)
	private int anioDesde;

	@Column(name = "anio_hasta", nullable = false)
	private int anioHasta;

	@Column(nullable = false)
	private int km;

	@Column(nullable = false)
	private int meses;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(name = "precio_estimado", nullable = false, precision = 10, scale = 2)
	private BigDecimal precioEstimado;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "concepto_mantenimiento", joinColumns = @JoinColumn(name = "plan_mantenimiento_id"))
	@Column(name = "concepto", nullable = false, length = 120)
	private List<String> conceptos = new ArrayList<>();

	protected PlanMantenimiento() {
		// Requerido por JPA
	}

	public Long getId() {
		return id;
	}

	public String getModelo() {
		return modelo;
	}

	public int getAnioDesde() {
		return anioDesde;
	}

	public int getAnioHasta() {
		return anioHasta;
	}

	public int getKm() {
		return km;
	}

	public int getMeses() {
		return meses;
	}

	public String getNombre() {
		return nombre;
	}

	public BigDecimal getPrecioEstimado() {
		return precioEstimado;
	}

	public List<String> getConceptos() {
		return conceptos;
	}
}
