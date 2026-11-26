package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoValuacion;
import java.time.Instant;
import java.util.List;

/**
 * Una valuación vista por ventas: el cliente, el auto con su rango estimado, la
 * cita y las fotos con URL firmada (V2-M26.04). El ERP está congelado, así que
 * no es parte del contrato de la app.
 */
public record ValuacionPersonalApp(Long id, EstadoValuacion estado, Instant creadaEn, Long clienteId, String cliente,
		Long vehiculoId, Long prospectoId, EstimacionTomaCuentaApp estimacion, List<ValuacionApp.Foto> fotos,
		ValuacionApp.Inspeccion cita, ValuacionApp.Oferta oferta, Instant respondidaEn) {
}
