package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un renglón de la ficha resumida de un modelo del catálogo (V2-M03.02). */
@Entity
@Table(name = "modelo_catalogo_especificacion")
public class EspecificacionModeloCatalogo extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "modelo_catalogo_id", nullable = false)
	private Long modeloCatalogoId;

	@Column(nullable = false, length = 60)
	private String etiqueta;

	@Column(nullable = false, length = 120)
	private String valor;

	@Column(nullable = false)
	private int orden;

	protected EspecificacionModeloCatalogo() {
		// Requerido por JPA
	}

	/**
	 * @throws IllegalArgumentException
	 *             si falta la etiqueta o el valor, pasan de su largo o el orden es
	 *             negativo (400)
	 */
	public static EspecificacionModeloCatalogo nueva(Long modeloCatalogoId, String etiqueta, String valor, int orden) {
		if (etiqueta == null || etiqueta.isBlank() || etiqueta.strip().length() > 60) {
			throw new IllegalArgumentException("La etiqueta es obligatoria y de hasta 60 caracteres");
		}
		if (valor == null || valor.isBlank() || valor.strip().length() > 120) {
			throw new IllegalArgumentException("El valor es obligatorio y de hasta 120 caracteres");
		}
		if (orden < 0) {
			throw new IllegalArgumentException("El orden no puede ser negativo");
		}
		EspecificacionModeloCatalogo nueva = new EspecificacionModeloCatalogo();
		nueva.modeloCatalogoId = modeloCatalogoId;
		nueva.etiqueta = etiqueta.strip();
		nueva.valor = valor.strip();
		nueva.orden = orden;
		return nueva;
	}

	public Long getId() {
		return id;
	}

	public Long getModeloCatalogoId() {
		return modeloCatalogoId;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	public String getValor() {
		return valor;
	}

	public int getOrden() {
		return orden;
	}
}
