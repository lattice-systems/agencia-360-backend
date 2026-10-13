package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import java.math.BigDecimal;

/**
 * Datos capturados para dar de alta una refacción (ERP-2.0). No trae
 * existencia: empieza en cero y crece con las recepciones.
 */
public record NuevaRefaccion(String codigo, String descripcion, BigDecimal costo, BigDecimal precio, int minimo) {
}
