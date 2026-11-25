package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada madrugada borra los archivos de acceso ARCO que pasaron su vigencia
 * (V2-M22.02): una copia de datos personales no se guarda más de lo necesario.
 */
@Component
class LimpiezaDeArco {

	private final ArcoService arco;

	LimpiezaDeArco(ArcoService arco) {
		this.arco = arco;
	}

	@Scheduled(cron = "0 30 3 * * *", zone = "America/Mexico_City")
	void limpiar() {
		arco.limpiarVencidos();
	}
}
