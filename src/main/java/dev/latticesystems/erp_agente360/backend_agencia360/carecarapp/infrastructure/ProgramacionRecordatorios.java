package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.RecordatoriosService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Revisa los recordatorios de cita cada
 * {@code app.push.recordatorios.cada-minutos}. Se apaga con
 * {@code app.push.recordatorios.habilitados=false} (en pruebas).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.push.recordatorios.habilitados", havingValue = "true", matchIfMissing = true)
class ProgramacionRecordatorios {

	private final RecordatoriosService recordatorios;

	ProgramacionRecordatorios(RecordatoriosService recordatorios) {
		this.recordatorios = recordatorios;
	}

	@Scheduled(fixedDelayString = "${app.push.recordatorios.cada-minutos:5}", initialDelayString = "1", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
	void revisar() {
		recordatorios.enviarPendientes();
	}
}
