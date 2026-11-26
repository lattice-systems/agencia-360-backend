package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/**
 * La garantía de un vehículo (V2-M10.01): cada componente cubierto contra el km
 * actual, el mismo que usa el mantenimiento (V2-M05.02).
 *
 * @param kmActual
 *            nulo si no se conoce el km del auto
 */
public record GarantiaApp(Long vehiculoId, Integer kmActual, List<CoberturaApp> coberturas) {
}
