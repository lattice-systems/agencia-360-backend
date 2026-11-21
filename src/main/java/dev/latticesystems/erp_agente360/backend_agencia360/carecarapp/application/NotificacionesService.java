package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioPush.Notificacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DispositivoApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Plataforma;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DispositivoAppRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teléfonos del cliente y envío de avisos a todos ellos (APP-6). */
@Service
public class NotificacionesService {

	private final DispositivoAppRepository repository;
	private final EnvioPush envioPush;

	public NotificacionesService(DispositivoAppRepository repository, EnvioPush envioPush) {
		this.repository = repository;
		this.envioPush = envioPush;
	}

	/**
	 * Registra el token del teléfono para el cliente. Si ya estaba con otro
	 * cliente, pasa a este.
	 *
	 * @throws IllegalArgumentException
	 *             si falta el token, pasa de 4096 caracteres o falta la plataforma
	 *             (400)
	 */
	@Transactional
	public void registrar(Long clienteId, String token, Plataforma plataforma) {
		if (token == null || token.isBlank() || token.length() > DispositivoApp.LARGO_MAXIMO_TOKEN) {
			throw new IllegalArgumentException("El token no es válido");
		}
		if (plataforma == null) {
			throw new IllegalArgumentException("La plataforma es obligatoria");
		}
		repository.findByToken(token).ifPresentOrElse(dispositivo -> dispositivo.asignar(clienteId, plataforma),
				() -> repository.save(new DispositivoApp(clienteId, token, plataforma)));
	}

	/** Borra el token si es del cliente; si no existe no hace nada. */
	@Transactional
	public void borrar(Long clienteId, String token) {
		if (token != null) {
			repository.borrar(clienteId, token);
		}
	}

	/**
	 * Manda el aviso a todos los teléfonos del cliente y olvida los tokens
	 * vencidos.
	 */
	@Transactional
	public void avisar(Long clienteId, Notificacion notificacion) {
		List<String> tokens = repository.findByClienteId(clienteId).stream().map(DispositivoApp::getToken).toList();
		if (tokens.isEmpty()) {
			return;
		}
		Set<String> vencidos = envioPush.enviar(tokens, notificacion);
		if (!vencidos.isEmpty()) {
			repository.borrarTokens(vencidos);
		}
	}
}
