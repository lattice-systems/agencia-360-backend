package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.LocalDate;

/**
 * Una cobertura de garantía en la app (V2-M10.01).
 *
 * @param diasRestantes
 *            días hasta {@code venceEn}; 0 si ya pasó
 * @param kmRestantes
 *            km hasta {@code kmLimite}; nulo si no tiene km límite o no se
 *            conoce el km del auto
 * @param consumo
 *            de 0 a 100, lo que ya se usó del tiempo o del km, lo que vaya más
 *            adelante
 * @param limite
 *            {@code fecha} o {@code km}: el límite que se alcanza primero (o el
 *            que la venció)
 */
public record CoberturaApp(Long id, String componente, String descripcion, LocalDate iniciaEn, LocalDate venceEn,
		Integer kmLimite, long diasRestantes, Integer kmRestantes, int consumo, EstadoCobertura estado, String limite) {
}
