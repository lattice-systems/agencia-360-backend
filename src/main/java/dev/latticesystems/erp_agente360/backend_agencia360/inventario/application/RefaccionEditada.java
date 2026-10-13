package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

import java.math.BigDecimal;

/** Datos corregidos de una refacción. La existencia no se edita. */
public record RefaccionEditada(String codigo, String descripcion, BigDecimal costo, BigDecimal precio, int minimo) {
}
