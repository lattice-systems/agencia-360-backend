package dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un modelo y rango de años a los que les queda una refacción (V2-M14.01). */
@Entity
@Table(name = "compatibilidad_refaccion")
public class CompatibilidadRefaccion {

	private static final int LARGO_MAXIMO_MODELO = 60;
	private static final int ANIO_MINIMO = 1950;
	private static final int ANIO_MAXIMO = 2100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "refaccion_id")
	private Refaccion refaccion;

	@Column(nullable = false, length = 60)
	private String modelo;

	@Column(name = "anio_desde", nullable = false)
	private int anioDesde;

	@Column(name = "anio_hasta", nullable = false)
	private int anioHasta;

	protected CompatibilidadRefaccion() {
		// Requerido por JPA
	}

	static CompatibilidadRefaccion de(Refaccion refaccion, String modelo, int anioDesde, int anioHasta) {
		String limpio = Texto.obligatorio(modelo, "El modelo compatible es obligatorio");
		if (limpio.length() > LARGO_MAXIMO_MODELO) {
			throw new IllegalArgumentException("El modelo admite hasta 60 caracteres");
		}
		if (anioDesde < ANIO_MINIMO || anioHasta > ANIO_MAXIMO || anioDesde > anioHasta) {
			throw new IllegalArgumentException("El rango de años no es válido");
		}
		CompatibilidadRefaccion compatibilidad = new CompatibilidadRefaccion();
		compatibilidad.refaccion = refaccion;
		compatibilidad.modelo = limpio;
		compatibilidad.anioDesde = anioDesde;
		compatibilidad.anioHasta = anioHasta;
		return compatibilidad;
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
}
