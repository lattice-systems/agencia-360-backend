package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.time.Instant;

/** Último punto que reportó el chofer de un traslado (V2-M17.03). */
public record UbicacionChoferApp(BigDecimal lat, BigDecimal lng, Instant actualizadaEn) {
}
