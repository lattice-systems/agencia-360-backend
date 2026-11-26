package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estados de una valuación formal (V2-M26.01 a V2-M26.04). */
public enum EstadoValuacion {

	SOLICITADA, CITA_AGENDADA, OFERTADA, ACEPTADA, RECHAZADA, VENCIDA;

	/** Mientras está en uno de estos no se abre otra para el mismo vehículo. */
	public static Set<EstadoValuacion> abiertos() {
		return EnumSet.of(SOLICITADA, CITA_AGENDADA, OFERTADA);
	}
}
