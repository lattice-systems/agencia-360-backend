package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.application;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
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
 */
@Service
public class RegistroProspectoService {

	static final String DUPLICADO = "Ya existe un prospecto con ese teléfono o correo";

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
	public ProspectoVista registrar(NuevoProspecto nuevo, UsuarioAutenticado vendedor) {
		Prospecto prospecto = Prospecto.registrar(nuevo.nombre(), nuevo.telefono(), nuevo.correo(), vendedor.id(),
				Instant.now(reloj));
		validarQueNoEsteRepetido(prospecto);
		return ProspectoVista.de(prospectoRepository.save(prospecto));
	}

	/**
	 * Busca en todos los prospectos, no solo en los del vendedor, y no dice de
	 * quién es el repetido para no romper la regla de visibilidad.
	 *
	 * <p>
	 * PENDIENTE P1 (PO): falta decidir si el duplicado bloquea el registro o solo
	 * avisa. Por ahora bloquea con 409.
	 */
	private void validarQueNoEsteRepetido(Prospecto prospecto) {
		boolean telefonoRepetido = prospecto.getTelefono() != null
				&& prospectoRepository.existsByTelefono(prospecto.getTelefono());
		boolean correoRepetido = prospecto.getCorreo() != null
				&& prospectoRepository.existsByCorreo(prospecto.getCorreo());
		if (telefonoRepetido || correoRepetido) {
			throw new ConflictoException(DUPLICADO);
		}
	}
}
