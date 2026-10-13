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

	private boolean tieneContactoRepetido(Prospecto prospecto) {
		return (prospecto.getTelefono() != null && prospectoRepository.existsByTelefono(prospecto.getTelefono()))
				|| (prospecto.getCorreo() != null && prospectoRepository.existsByCorreo(prospecto.getCorreo()));
	}
}
