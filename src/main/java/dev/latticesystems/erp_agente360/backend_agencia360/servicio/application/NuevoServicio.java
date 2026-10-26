package dev.latticesystems.erp_agente360.backend_agencia360.servicio.application;

import dev.latticesystems.erp_agente360.backend_agencia360.servicio.domain.RenglonNuevo;
import java.time.LocalDate;
import java.util.List;

/** Servicio que Servicio registra en el expediente de un vehículo. */
public record NuevoServicio(LocalDate fecha, int kilometraje, List<RenglonNuevo> renglones) {
}
