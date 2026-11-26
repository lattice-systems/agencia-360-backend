package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.NivelLealtad;

/**
 * Mis puntos (V2-M12.01).
 *
 * @param ganados
 *            puntos ganados en los últimos 12 meses, que deciden el nivel
 * @param siguienteNivel
 *            {@code null} si ya está en Oro
 * @param puntosParaSiguiente
 *            los que le faltan ganar; 0 si ya está en Oro
 * @param codigoReferido
 *            su código para referir amigos (V2-M12.04)
 */
public record ResumenLealtadApp(long saldo, NivelLealtad nivel, long ganados, NivelLealtad siguienteNivel,
		long puntosParaSiguiente, String codigoReferido) {
}
