package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

/**
 * Qué tan grave es una campaña de seguridad (V2-M10.02). Una {@link #CRITICA}
 * se avisa aunque el cliente haya silenciado los avisos (V2-M10.04).
 */
public enum GravedadCampana {
	BAJA, MEDIA, ALTA, CRITICA;

	public boolean critica() {
		return this == CRITICA;
	}
}
