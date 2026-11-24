package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Orden que Servicio abre cuando el vehículo entra al taller (V2-M04.01). La
 * entrega estimada va en hora de la agencia, sin zona.
 */
public record AbrirOrdenRequest(@NotNull Long vehiculoId, @NotNull @Min(0) @Max(2_000_000) Integer kilometraje,
		Long citaId, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime entregaEstimada) {
}
