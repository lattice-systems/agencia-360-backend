package dev.latticesystems.erp_agente360.backend_agencia360.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas periódicas ({@code @Scheduled}), como el latido que
 * mantiene vivas las conexiones del inventario en tiempo real (ERP-2.1).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
