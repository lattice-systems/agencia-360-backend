package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de prospectos (CRM-1.0). El prospecto queda a cargo del vendedor que
 * lo captura.
 *
 * <p>
 * Decisión del PO del 12 de octubre: un teléfono o correo repetido no bloquea
 * el registro, porque un cliente suele hablar con más de un vendedor. Solo se
 * avisa, sin decir de quién es el otro prospecto; cada vendedor conserva el
 * suyo.
 */
@Service
public class RegistroProspectoService {

	private final ProspectoRepository prospectoRepository;
	private final Clock reloj;

	@Autowired
	public RegistroProspectoService(ProspectoRepository prospectoRepository) {
		this(prospectoRepository, Clock.systemUTC());
	}

	RegistroProspectoService(ProspectoRepository prospectoRepository, Clock reloj) {
		this.prospectoRepository = prospectoRepository;
		this.reloj = reloj;
	}

	@Transactional
	public ProspectoRegistrado registrar(NuevoProspecto nuevo, UsuarioAutenticado vendedor) {
		Prospecto prospecto = Prospecto.registrar(nuevo.nombre(), nuevo.telefono(), nuevo.correo(), vendedor.id(),
				Instant.now(reloj));
		// Se busca antes de guardar para no encontrarse a sí mismo.
		boolean contactoRepetido = tieneContactoRepetido(prospecto);
		return new ProspectoRegistrado(ProspectoVista.de(prospectoRepository.save(prospecto)), contactoRepetido);
	}

	/**
	 * Revisa, mientras el vendedor captura, si el teléfono o el correo ya existen
	 * en otro prospecto. No dice de quién es. Un teléfono incompleto todavía no se
	 * compara.
	 */
	@Transactional(readOnly = true)
	public boolean hayContactoRepetido(String telefono, String correo) {
		String telefonoNormalizado;
		try {
			telefonoNormalizado = Prospecto.normalizarTelefono(telefono);
		} catch (IllegalArgumentException incompleto) {
			telefonoNormalizado = null;
		}
		return existe(telefonoNormalizado, Prospecto.normalizarCorreo(correo));
	}

	private boolean tieneContactoRepetido(Prospecto prospecto) {
		return existe(prospecto.getTelefono(), prospecto.getCorreo());
	}

	private boolean existe(String telefono, String correo) {
		return (telefono != null && prospectoRepository.existsByTelefono(telefono))
				|| (correo != null && prospectoRepository.existsByCorreo(correo));
	}
}
