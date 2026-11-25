package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisosPrivacidad.Aceptacion;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisosPrivacidad.Aviso;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aviso de privacidad versionado (V2-M22.01): cuál es el vigente, quién lo
 * aceptó y cómo publica la agencia una versión nueva. Un aviso publicado no se
 * edita (lo impide también un disparador en la base).
 */
@Service
public class PrivacidadService {

	static final int LARGO_MAXIMO = 20_000;

	/**
	 * Lo que tarda el reloj de quien publica en no estar adelantado al servidor.
	 */
	private static final Duration TOLERANCIA = Duration.ofMinutes(1);

	/** El aviso vigente tal como lo ve un cliente. */
	public record AvisoApp(long id, int version, String texto, Instant vigenteDesde, boolean aceptado,
			Instant aceptadoEn) {
	}

	/** La aceptación registrada. */
	public record AceptacionApp(long avisoId, int version, Instant aceptadoEn) {
	}

	private final AvisosPrivacidad avisos;
	private final Clock reloj;

	@Autowired
	public PrivacidadService(AvisosPrivacidad avisos) {
		this(avisos, Clock.systemUTC());
	}

	public PrivacidadService(AvisosPrivacidad avisos, Clock reloj) {
		this.avisos = avisos;
		this.reloj = reloj;
	}

	/**
	 * El aviso vigente y si este cliente ya lo aceptó.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si todavía no hay ninguno publicado (404)
	 */
	@Transactional(readOnly = true)
	public AvisoApp vigente(long clienteId) {
		Aviso aviso = avisos.vigente(Instant.now(reloj))
				.orElseThrow(() -> new RecursoNoEncontradoException("Todavía no hay un aviso de privacidad publicado"));
		Optional<Aceptacion> aceptacion = avisos.aceptacionDe(clienteId, aviso.id());
		return new AvisoApp(aviso.id(), aviso.version(), aviso.texto(), aviso.vigenteDesde(), aceptacion.isPresent(),
				aceptacion.map(Aceptacion::aceptadoEn).orElse(null));
	}

	/**
	 * Registra que el cliente aceptó el aviso que leyó. Repetirlo devuelve la
	 * primera aceptación.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el aviso no existe (404)
	 * @throws ConflictoException
	 *             si ya hay una versión más reciente que la que leyó (409,
	 *             {@code AVISO_NO_VIGENTE})
	 */
	@Transactional
	public AceptacionApp aceptar(long clienteId, long avisoId) {
		Instant ahora = Instant.now(reloj);
		Aviso pedido = avisos.buscar(avisoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el aviso de privacidad " + avisoId));
		Aviso vigente = avisos.vigente(ahora)
				.orElseThrow(() -> new RecursoNoEncontradoException("Todavía no hay un aviso de privacidad publicado"));
		if (pedido.id() != vigente.id()) {
			throw new ConflictoException("Hay un aviso de privacidad más reciente: léelo y acéptalo.",
					CodigosError.AVISO_NO_VIGENTE);
		}
		Aceptacion aceptacion = avisos.aceptar(clienteId, avisoId, ahora);
		return new AceptacionApp(aceptacion.avisoId(), aceptacion.version(), aceptacion.aceptadoEn());
	}

	/**
	 * Publica una versión nueva. Sin {@code vigenteDesde} rige desde ahora; con él
	 * puede programarse, pero no retroceder.
	 *
	 * @throws SolicitudInvalidaException
	 *             texto vacío o de más de 20 000 caracteres, o una fecha anterior a
	 *             ahora o a la de la versión previa (400)
	 */
	@Transactional
	public AvisoApp publicar(String texto, Instant vigenteDesde) {
		if (texto == null || texto.isBlank()) {
			throw new SolicitudInvalidaException("El texto del aviso no puede ir vacío", CodigosError.DATOS_INVALIDOS);
		}
		String limpio = texto.strip();
		if (limpio.length() > LARGO_MAXIMO) {
			throw new SolicitudInvalidaException("El texto del aviso no puede pasar de " + LARGO_MAXIMO + " caracteres",
					CodigosError.DATOS_INVALIDOS);
		}
		Instant ahora = Instant.now(reloj);
		Instant rige = vigenteDesde == null ? ahora : vigenteDesde;
		if (rige.isBefore(ahora.minus(TOLERANCIA))) {
			throw new SolicitudInvalidaException("La fecha desde la que rige no puede ser anterior a ahora",
					CodigosError.DATOS_INVALIDOS);
		}
		avisos.ultima().ifPresent(previa -> {
			if (rige.isBefore(previa.vigenteDesde())) {
				throw new SolicitudInvalidaException(
						"La versión nueva no puede regir antes que la versión " + previa.version(),
						CodigosError.DATOS_INVALIDOS);
			}
		});
		Aviso nuevo = avisos.publicar(limpio, rige);
		return new AvisoApp(nuevo.id(), nuevo.version(), nuevo.texto(), nuevo.vigenteDesde(), false, null);
	}
}
