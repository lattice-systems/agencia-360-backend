package dev.latticesystems.erp_agente360.backend_agencia360.inventario.application;

/**
 * Cambió una unidad del inventario: alta, corrección y, más adelante, la venta
 * (ERP-2.1). Es interno del módulo; lo escucha el aviso en tiempo real después
 * del commit, así que una operación que se revierte no avisa.
 */
public record VehiculoCambiado(Long vehiculoId) {
}
