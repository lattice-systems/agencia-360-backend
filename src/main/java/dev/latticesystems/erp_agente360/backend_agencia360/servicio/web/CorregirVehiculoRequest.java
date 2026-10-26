package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Placas y kilometraje que corrige Servicio; vacíos quedan sin capturar. */
public record CorregirVehiculoRequest(@Size(max = 15) String placas, @Min(0) @Max(2_000_000) Integer kilometraje) {
}
