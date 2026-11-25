package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un servicio del plan de mantenimiento (V2-M05.01 y V2-M05.06). El
 * {@code estado} es {@code hecho}, {@code pendiente}, {@code proximo} o
 * {@code vencido}; {@code hechoEn} solo viene si está hecho. El precio es una
 * estimación no vinculante.
 */
public record ServicioMantenimientoApp(Long id, String nombre, int km, int meses, BigDecimal precioEstimado,
		List<String> conceptos, String estado, LocalDate hechoEn) {
}
