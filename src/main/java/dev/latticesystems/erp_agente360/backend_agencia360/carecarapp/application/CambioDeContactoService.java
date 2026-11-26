package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDeContacto;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.VerificacionContacto;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.VerificacionContactoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.DirectorioClientes.ClienteRegistrado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ProhibidoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambiar el correo del cliente, confirmándolo con un código (V2-M01.07).
 *
 * <p>
 * <b>Solo el correo.</b> Verificar el teléfono exige mandar un código al
 * teléfono y no hay SMS en ningún lado; mandarlo al correo confirmaría que la
 * cuenta autorizó el cambio, no que el teléfono sea suyo. La tabla ya acepta
 * {@link TipoDeContacto#TELEFONO} para cuando exista.
 *
 * <p>
 * El correo <b>no se guarda hasta confirmar</b>: vive en la verificación. Como
 * {@code cuenta_app.usuario} sigue al correo (V2-T05.03), guardarlo antes
 * dejaría al cliente sin poder entrar con un correo que no ha probado que es
 * suyo.
 */
@Service
public class CambioDeContactoService {

	static final String CUENTA_DESACTIVADA = "Tu cuenta de CareCar está desactivada. Comunícate con la agencia.";
	static final String CODIGO_NO_VALIDO = "El código no es válido, ya se usó o venció";

	private final DirectorioClientes directorioClientes;
	private final CuentaAppRepository cuentaRepository;
	private final VerificacionContactoRepository verificacionRepository;
	private final PasswordEncoder passwordEncoder;
	private final EnvioCorreos envioCorreos;
	private final Clock reloj;

	public CambioDeContactoService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			VerificacionContactoRepository verificacionRepository, PasswordEncoder passwordEncoder,
			EnvioCorreos envioCorreos) {
		this(directorioClientes, cuentaRepository, verificacionRepository, passwordEncoder, envioCorreos,
				Clock.systemUTC());
	}

	CambioDeContactoService(DirectorioClientes directorioClientes, CuentaAppRepository cuentaRepository,
			VerificacionContactoRepository verificacionRepository, PasswordEncoder passwordEncoder,
			EnvioCorreos envioCorreos, Clock reloj) {
		this.directorioClientes = directorioClientes;
		this.cuentaRepository = cuentaRepository;
		this.verificacionRepository = verificacionRepository;
		this.passwordEncoder = passwordEncoder;
		this.envioCorreos = envioCorreos;
		this.reloj = reloj;
	}

	/**
	 * Crea la verificación y manda el código al correo nuevo. <b>No cambia
	 * nada.</b>
	 *
	 * @throws SolicitudInvalidaException
	 *             la contraseña no coincide (400 con
	 *             {@code CREDENCIALES_INVALIDAS}, no 401: la app lee un 401 como
	 *             sesión vencida y cerraría la sesión sin explicar nada) o el
	 *             correo no tiene formato (400 con {@code DATOS_INVALIDOS})
	 * @throws ConflictoException
	 *             ese correo ya es de otro cliente (409)
	 */
	@Transactional
	public VerificacionCreada pedirCambioDeCorreo(Long clienteId, String correoNuevo, String contrasenaActual) {
		Instant ahora = Instant.now(reloj);
		CuentaApp cuenta = cuentaDe(clienteId);
		exigirActiva(cuenta);

		if (!passwordEncoder.matches(contrasenaActual, cuenta.getContrasenaHash())) {
			throw new SolicitudInvalidaException("La contraseña no coincide", CodigosError.CREDENCIALES_INVALIDAS);
		}

		String normalizado = normalizarCorreo(correoNuevo);
		// Que ya sea de otro cliente se sabe aquí, antes de mandar nada: de otro
		// modo el cliente escribiría un código para que luego fallara.
		directorioClientes.buscarPorCorreo(normalizado).filter(otro -> !otro.id().equals(clienteId)).ifPresent(otro -> {
			throw new ConflictoException("Ese correo ya es de otro cliente");
		});

		ClienteRegistrado cliente = directorioClientes.buscarPorId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + clienteId));

		String codigo = Secretos.nuevoCodigo();
		VerificacionContacto verificacion = verificacionRepository.save(VerificacionContacto.emitir(clienteId,
				TipoDeContacto.CORREO, normalizado, passwordEncoder.encode(codigo), ahora));
		// Al correo NUEVO: es el único destino que prueba algo.
		envioCorreos.enviarCodigoDeContacto(normalizado, cliente.nombre(), codigo);

		return new VerificacionCreada(verificacion.getId(), verificacion.getExpira());
	}

	/**
	 * Aplica el cambio si el código es el correcto.
	 *
	 * @throws RecursoNoEncontradoException
	 *             no existe esa verificación o no es de este cliente (404, nunca
	 *             403: un 403 diría «existe, pero no es tuya»)
	 * @throws SolicitudInvalidaException
	 *             el código es equivocado, ya se usó o venció. Los tres responden
	 *             igual a propósito: responder distinto permitiría averiguar qué
	 *             códigos existen
	 */
	@Transactional
	public PerfilApp confirmar(Long clienteId, Long verificacionId, String codigo) {
		Instant ahora = Instant.now(reloj);
		CuentaApp cuenta = cuentaDe(clienteId);
		exigirActiva(cuenta);

		VerificacionContacto verificacion = verificacionRepository.findByIdAndClienteId(verificacionId, clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe esa verificación"));

		if (!verificacion.estaVigente(ahora)) {
			throw codigoNoValido();
		}
		if (!passwordEncoder.matches(codigo, verificacion.getCodigoHash())) {
			verificacion.registrarIntentoFallido();
			throw codigoNoValido();
		}

		verificacion.cerrar(ahora);
		// `cambiarCorreo` avisa con `CorreoDeClienteCambiado` para que
		// `cuenta_app.usuario` siga al correo (V2-T05.03), y lanza 409 si alguien
		// tomó ese correo mientras se verificaba.
		ClienteRegistrado actualizado = directorioClientes.cambiarCorreo(clienteId, verificacion.getValorNuevo());

		return new PerfilApp(actualizado.id(), actualizado.nombre(), actualizado.correo(), actualizado.telefono(),
				LocalDate.ofInstant(actualizado.registradoEn(), ServicioApp.ZONA_AGENCIA));
	}

	private static SolicitudInvalidaException codigoNoValido() {
		return new SolicitudInvalidaException(CODIGO_NO_VALIDO, CodigosError.CODIGO_INVALIDO);
	}

	/**
	 * Mismo criterio que el resto del contrato: lo que no tiene arroba con algo a
	 * cada lado no es un correo. Se revisa aquí para que el formato y la contraseña
	 * no acaben compartiendo código de error.
	 */
	private static String normalizarCorreo(String correo) {
		String limpio = correo == null ? "" : correo.trim().toLowerCase();
		if (!limpio.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new SolicitudInvalidaException("Ese correo no tiene formato válido", CodigosError.DATOS_INVALIDOS);
		}
		return limpio;
	}

	private CuentaApp cuentaDe(Long clienteId) {
		return cuentaRepository.findByClienteId(clienteId)
				.orElseThrow(() -> new ProhibidoException(CUENTA_DESACTIVADA, CodigosError.CUENTA_DESACTIVADA));
	}

	private static void exigirActiva(CuentaApp cuenta) {
		if (!cuenta.isActiva()) {
			throw new ProhibidoException(CUENTA_DESACTIVADA, CodigosError.CUENTA_DESACTIVADA);
		}
	}

	/** La verificación que se acaba de crear. No devuelve el correo nuevo. */
	public record VerificacionCreada(Long id, Instant expira) {
	}
}
