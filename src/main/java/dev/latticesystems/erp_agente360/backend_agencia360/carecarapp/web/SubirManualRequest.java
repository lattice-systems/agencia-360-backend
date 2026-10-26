package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manual que sube el Gerente de marketing (multipart/form-data). {@code modelo}
 * es el Tipo (March) y {@code anio} el Modelo (2026). Es una clase con setters,
 * no un record, para que Spring enlace el archivo del formulario.
 */
public class SubirManualRequest {

	@NotBlank
	@Size(max = 40)
	private String marca;

	@NotBlank
	@Size(max = 80)
	private String modelo;

	@NotNull
	@Min(1900)
	private Integer anio;

	@NotNull
	private MultipartFile archivo;

	public String getMarca() {
		return marca;
	}

	public void setMarca(String marca) {
		this.marca = marca;
	}

	public String getModelo() {
		return modelo;
	}

	public void setModelo(String modelo) {
		this.modelo = modelo;
	}

	public Integer getAnio() {
		return anio;
	}

	public void setAnio(Integer anio) {
		this.anio = anio;
	}

	public MultipartFile getArchivo() {
		return archivo;
	}

	public void setArchivo(MultipartFile archivo) {
		this.archivo = archivo;
	}
}
