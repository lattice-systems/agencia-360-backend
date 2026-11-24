package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoAsistencia;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Solicitud de asistencia que pide el cliente (V2-M11.01). La ubicación es
 * opcional pero va completa: latitud y longitud juntas.
 */
public record NuevaSolicitudAsistenciaRequest(@NotNull Long vehiculoId, @NotNull TipoAsistencia tipo,
		@DecimalMin("-90") @DecimalMax("90") BigDecimal lat, @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
		@Size(max = 500) String comentario) {
}
