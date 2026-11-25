package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checklist que carga el chofer (multipart/form-data). Es una clase con
 * setters, no un record, para que Spring enlace los archivos del formulario.
 * Los datos se validan en el dominio (400).
 */
public class CargarChecklistRequest {

	private Integer km;
	private String gasolina;
	private String notas;
	private List<MultipartFile> fotos = new ArrayList<>();
	private MultipartFile firma;

	public Integer getKm() {
		return km;
	}

	public void setKm(Integer km) {
		this.km = km;
	}

	public String getGasolina() {
		return gasolina;
	}

	public void setGasolina(String gasolina) {
		this.gasolina = gasolina;
	}

	public String getNotas() {
		return notas;
	}

	public void setNotas(String notas) {
		this.notas = notas;
	}

	public List<MultipartFile> getFotos() {
		return fotos;
	}

	public void setFotos(List<MultipartFile> fotos) {
		this.fotos = fotos == null ? new ArrayList<>() : fotos;
	}

	public MultipartFile getFirma() {
		return firma;
	}

	public void setFirma(MultipartFile firma) {
		this.firma = firma;
	}
}
