package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una especificación de la ficha técnica de un modelo (V2-M02.04), dentro de
 * una sección (Motor, Dimensiones…). Como en el inventario, {@code modelo} es
 * el Tipo (Sentra) y {@code anio} el Modelo (2026).
 */
@Entity
@Table(name = "ficha_tecnica_modelo")
public class FichaTecnicaModelo extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40)
	private String marca;

	@Column(nullable = false, length = 80)
	private String modelo;

	@Column(nullable = false)
	private int anio;

	@Column(nullable = false, length = 60)
	private String seccion;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, length = 200)
	private String valor;

	@Column(nullable = false)
	private int orden;

	protected FichaTecnicaModelo() {
		// Requerido por JPA
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta un texto o el año es menor a 1900 (400)
	 */
	public static FichaTecnicaModelo de(String marca, String modelo, int anio, String seccion, String nombre,
			String valor, int orden) {
		for (String texto : new String[]{marca, modelo, seccion, nombre, valor}) {
			if (texto == null || texto.isBlank()) {
				throw new IllegalArgumentException("La marca, el modelo, la sección, el nombre y el valor son obligatorios");
			}
		}
		if (anio < 1900) {
			throw new IllegalArgumentException("El año debe ser 1900 o mayor");
		}
		FichaTecnicaModelo ficha = new FichaTecnicaModelo();
		ficha.marca = marca.trim();
		ficha.modelo = modelo.trim();
		ficha.anio = anio;
		ficha.seccion = seccion.trim();
		ficha.nombre = nombre.trim();
		ficha.valor = valor.trim();
		ficha.orden = orden;
		return ficha;
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

	public String getSeccion() {
		return seccion;
	}

	public String getNombre() {
		return nombre;
	}

	public String getValor() {
		return valor;
	}

	public int getOrden() {
		return orden;
	}
}
