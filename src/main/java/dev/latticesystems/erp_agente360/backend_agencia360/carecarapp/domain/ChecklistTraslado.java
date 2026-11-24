package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Checklist que el chofer llena al recoger o entregar el vehículo (V2-M17.04):
 * kilometraje, gasolina, notas, fotos y firma. Hay uno por traslado y volver a
 * cargarlo lo reemplaza. {@code fotos} y {@code firmaUrl} guardan claves de
 * {@code AlmacenArchivos}, no URL: la URL firmada se arma al consultar.
 */
@Entity
@Table(name = "checklist_traslado")
public class ChecklistTraslado extends Auditable {

	public static final int NOTAS_MAXIMO = 500;
	public static final int FOTOS_MAXIMO = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "traslado_id", nullable = false, updatable = false)
	private Long trasladoId;

	@Column(nullable = false)
	private int km;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private Gasolina gasolina;

	@Column(length = NOTAS_MAXIMO)
	private String notas;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<String> fotos = new ArrayList<>();

	@Column(name = "firma_url", length = 500)
	private String firmaUrl;

	protected ChecklistTraslado() {
		// Requerido por JPA
	}

	/**
	 * Un checklist nuevo.
	 *
	 * @param fotos
	 *            claves de archivo, hasta {@value #FOTOS_MAXIMO}
	 * @param firma
	 *            clave del archivo de la firma; nula si no hay
	 * @throws IllegalArgumentException
	 *             si el km no está entre 1 y 2 000 000, falta la gasolina, las
	 *             notas pasan de 500 o hay más de 10 fotos (400)
	 */
	public static ChecklistTraslado registrar(Long trasladoId, int km, Gasolina gasolina, String notas,
			List<String> fotos, String firma) {
		Objects.requireNonNull(trasladoId, "trasladoId");
		ChecklistTraslado nuevo = new ChecklistTraslado();
		nuevo.trasladoId = trasladoId;
		nuevo.reemplazar(km, gasolina, notas, fotos, firma);
		return nuevo;
	}

	/**
	 * Cambia todo el contenido.
	 *
	 * @return las claves de los archivos que ya no se usan, para borrarlos
	 */
	public List<String> reemplazar(int km, Gasolina gasolina, String notas, List<String> fotos, String firma) {
		LecturaKm.exigirKmValido(km);
		if (gasolina == null) {
			throw new IllegalArgumentException("La gasolina es obligatoria");
		}
		String limpias = notas == null || notas.isBlank() ? null : notas.strip();
		if (limpias != null && limpias.length() > NOTAS_MAXIMO) {
			throw new IllegalArgumentException("Las notas pasan de " + NOTAS_MAXIMO + " caracteres");
		}
		if (fotos.size() > FOTOS_MAXIMO) {
			throw new IllegalArgumentException("Se admiten hasta " + FOTOS_MAXIMO + " fotos");
		}
		List<String> sobrantes = new ArrayList<>(archivos());
		this.km = km;
		this.gasolina = gasolina;
		this.notas = limpias;
		this.fotos = new ArrayList<>(fotos);
		this.firmaUrl = firma;
		sobrantes.removeAll(archivos());
		return sobrantes;
	}

	/** Todas las claves de archivo que guarda: fotos y firma. */
	public List<String> archivos() {
		List<String> claves = new ArrayList<>(fotos);
		if (firmaUrl != null) {
			claves.add(firmaUrl);
		}
		return claves;
	}

	public Long getId() {
		return id;
	}

	public Long getTrasladoId() {
		return trasladoId;
	}

	public int getKm() {
		return km;
	}

	public Gasolina getGasolina() {
		return gasolina;
	}

	public String getNotas() {
		return notas;
	}

	public List<String> getFotos() {
		return List.copyOf(fotos);
	}

	public String getFirmaUrl() {
		return firmaUrl;
	}
}
