package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sin credenciales de Firebase: solo deja en el log lo que habría enviado. */
class PushSoloRegistro implements EnvioPush {

	private static final Logger LOG = LoggerFactory.getLogger(PushSoloRegistro.class);

	@Override
	public Set<String> enviar(List<String> tokens, Notificacion notificacion) {
		LOG.info("Push sin Firebase configurado: «{}» a {} teléfono(s)", notificacion.titulo(), tokens.size());
		return Set.of();
	}
}
