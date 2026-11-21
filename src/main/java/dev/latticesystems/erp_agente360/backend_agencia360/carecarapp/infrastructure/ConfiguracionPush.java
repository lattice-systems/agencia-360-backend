package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige cómo se mandan los push: por Firebase si
 * {@code app.push.fcm.credenciales} apunta al JSON de la cuenta de servicio, o
 * solo al log si está vacío. Así la API arranca y pasa sus pruebas sin
 * credenciales.
 */
@Configuration
class ConfiguracionPush {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionPush.class);

	@Bean
	EnvioPush envioPush(@Value("${app.push.fcm.credenciales:}") String rutaCredenciales,
			@Value("${app.push.fcm.proyecto:}") String proyecto) throws IOException {
		if (rutaCredenciales == null || rutaCredenciales.isBlank()) {
			LOG.info("Sin FCM_CREDENCIALES: los push solo se registran en el log");
			return new PushSoloRegistro();
		}
		GoogleCredentials credenciales;
		try (InputStream archivo = new FileInputStream(rutaCredenciales)) {
			credenciales = GoogleCredentials.fromStream(archivo);
		}
		String idProyecto = proyecto;
		if ((idProyecto == null || idProyecto.isBlank()) && credenciales instanceof ServiceAccountCredentials cuenta) {
			idProyecto = cuenta.getProjectId();
		}
		if (idProyecto == null || idProyecto.isBlank()) {
			throw new IllegalStateException("Falta FCM_PROYECTO: las credenciales no traen el id del proyecto");
		}
		return new PushFcm(credenciales, idProyecto);
	}
}
