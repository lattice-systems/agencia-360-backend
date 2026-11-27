package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;

/**
 * Horario de un departamento ({@code HorarioSucursal} del contrato de la app).
 *
 * @param dias
 *            días ISO, 1 lunes a 7 domingo
 * @param abiertoAhora
 *            calculado con la hora de la agencia
 */
public record HorarioSucursalApp(String departamento, List<Integer> dias, String abre, String cierra,
		boolean abiertoAhora) {
}
