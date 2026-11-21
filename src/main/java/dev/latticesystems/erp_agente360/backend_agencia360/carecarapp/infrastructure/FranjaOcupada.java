package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import java.time.LocalDate;

/** Cuántas citas confirmadas tiene una franja de un día. */
public record FranjaOcupada(LocalDate fecha, Long franjaId, long citas) {
}
