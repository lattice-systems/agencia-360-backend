package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Punto que reporta el chofer; el rango lo revisa el dominio (400). */
public record UbicacionChoferRequest(@NotNull BigDecimal lat, @NotNull BigDecimal lng) {
}
