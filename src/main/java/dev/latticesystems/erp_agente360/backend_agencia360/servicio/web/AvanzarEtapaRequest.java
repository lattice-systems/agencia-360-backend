package dev.latticesystems.erp_agente360.backend_agencia360.servicio.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.latticesystems.erp_agente360.backend_agencia360.servicio.EtapaTaller;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Etapa a la que pasa la orden (V2-M04.02), con una nota opcional y, si cambia,
 * la nueva entrega estimada en hora de la agencia.
 */
public record AvanzarEtapaRequest(@NotNull EtapaTaller etapa, @Size(max = 300) String nota,
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime entregaEstimada) {
}
