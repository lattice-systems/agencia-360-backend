package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Credenciales de FCM (V2-M06.07). Las dos son opcionales: si falta alguna, el
 * push no se intenta y el arranque nunca falla. Solo viven en el ambiente (R8):
 * el JSON de la cuenta de servicio va en base64.
 */
@Component
public class ConfiguracionPush {

	private final String proyectoId;
	private final String cuentaDeServicioBase64;

	@Autowired
	public ConfiguracionPush(@Value("${app.push.fcm.project-id:}") String proyectoId,
			@Value("${app.push.fcm.service-account-json-base64:}") String cuentaDeServicioBase64) {
		this.proyectoId = proyectoId;
		this.cuentaDeServicioBase64 = cuentaDeServicioBase64;
	}

	/** @return si están las dos credenciales de FCM */
	public boolean configurado() {
		return hay(proyectoId) && hay(cuentaDeServicioBase64);
	}

	String proyectoId() {
		return proyectoId;
	}

	String cuentaDeServicioBase64() {
		return cuentaDeServicioBase64;
	}

	private static boolean hay(String valor) {
		return valor != null && !valor.isBlank();
	}
}
