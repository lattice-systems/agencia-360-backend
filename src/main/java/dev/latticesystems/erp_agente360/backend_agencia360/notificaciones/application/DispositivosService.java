package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Registro y baja del token de push de una instalación (V2-M06.02): la app lo
 * registra tras iniciar sesión y lo da de baja antes de cerrarla.
 */
@Service
public class DispositivosService {

	static final String IDIOMA_POR_DEFECTO = "es";
	private static final Pattern INSTALACION = Pattern.compile("[A-Za-z0-9-]{8,64}");
	private static final Set<String> PLATAFORMAS = Set.of("android", "ios");

	private final Dispositivos dispositivos;

	public DispositivosService(Dispositivos dispositivos) {
		this.dispositivos = dispositivos;
	}

	/**
	 * Registra o actualiza el token de esta instalación a nombre del cliente.
	 *
	 * @throws IllegalArgumentException
	 *             si la instalación o la plataforma no son válidas (400)
	 * @throws RecursoNoEncontradoException
	 *             si el cliente no tiene cuenta de la app (404)
	 */
	public void registrar(Long clienteId, String instalacionId, String tokenFcm, String plataforma, String idioma) {
		validarInstalacion(instalacionId);
		String normalizada = plataforma == null ? "" : plataforma.toLowerCase(Locale.ROOT);
		if (!PLATAFORMAS.contains(normalizada)) {
			throw new IllegalArgumentException("La plataforma debe ser android o ios");
		}
		String idiomaFinal = idioma == null || idioma.isBlank() ? IDIOMA_POR_DEFECTO : idioma;
		if (!dispositivos.registrar(clienteId, instalacionId, tokenFcm, normalizada, idiomaFinal)) {
			throw new RecursoNoEncontradoException("El cliente " + clienteId + " no tiene cuenta de la app");
		}
	}

	/**
	 * Da de baja la instalación si es de este cliente; si no existe o es de otra
	 * cuenta no hace nada, para no revelar si existe.
	 */
	public void darDeBaja(Long clienteId, String instalacionId) {
		validarInstalacion(instalacionId);
		dispositivos.darDeBaja(clienteId, instalacionId);
	}

	private void validarInstalacion(String instalacionId) {
		if (instalacionId == null || !INSTALACION.matcher(instalacionId).matches()) {
			throw new IllegalArgumentException(
					"El identificador de la instalación debe tener de 8 a 64 letras, " + "números o guiones");
		}
	}
}
