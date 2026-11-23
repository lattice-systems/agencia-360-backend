package dev.latticesystems.erp_agente360.backend_agencia360.servicio;

public enum EstatusOrden {
	RECIBIDO, EN_PROCESO, TERMINADO, ENTREGADO;

	public boolean cerrada() {
		return this == TERMINADO || this == ENTREGADO;
	}
}
