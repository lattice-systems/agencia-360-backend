package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Manual;

/** Manual como lo ve el personal de la agencia al subirlo. */
public record ManualVista(Long id, String marca, String modelo, int anio, String titulo, long tamanoBytes) {

	static ManualVista de(Manual manual) {
		return new ManualVista(manual.getId(), manual.getMarca(), manual.getModelo(), manual.getAnio(), manual.titulo(),
				manual.getTamanoBytes());
	}
}
