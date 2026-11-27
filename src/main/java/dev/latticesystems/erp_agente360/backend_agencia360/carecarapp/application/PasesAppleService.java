package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Cita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoCita;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.RegistroPaseApple;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CitaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.RegistroPaseAppleRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio web de pases de Apple Wallet (V2-M25.06): registra los dispositivos
 * que guardaron el pase de una cita, dice cuáles pases cambiaron y entrega el
 * pase vigente. No guarda el estado del pase: lo arma al vuelo desde la cita
 * (cancelada, {@code voided}; reprogramada, con la fecha nueva) y usa
 * {@code cita.updated_at} como marca de actualización.
 *
 * <p>
 * Sin credenciales de Apple ni URL pública HTTPS todo responde "no encontrado":
 * el servicio está apagado.
 */
@Service
public class PasesAppleService {

	static final String PREFIJO_SERIAL = "cita-";
	private static final int MAXIMO_DISPOSITIVO = 100;
	private static final int MAXIMO_PUSH_TOKEN = 200;
	private static final int MAXIMO_LOGS = 20;
	private static final int MAXIMO_LARGO_LOG = 500;
	private static final Logger LOG = LoggerFactory.getLogger(PasesAppleService.class);

	private final ConfiguracionWallet configuracion;
	private final TokenPaseApple tokens;
	private final RegistroPaseAppleRepository registros;
	private final CitaRepository citas;
	private final VehiculosDeClientes vehiculos;
	private final FirmaCitas firmaCitas;
	private final PaseAppleGenerador generador;

	@Autowired
	public PasesAppleService(ConfiguracionWallet configuracion, TokenPaseApple tokens,
			RegistroPaseAppleRepository registros, CitaRepository citas, VehiculosDeClientes vehiculos,
			FirmaCitas firmaCitas, PaseAppleGenerador generador) {
		this.configuracion = configuracion;
		this.tokens = tokens;
		this.registros = registros;
		this.citas = citas;
		this.vehiculos = vehiculos;
		this.firmaCitas = firmaCitas;
		this.generador = generador;
	}

	/** Cómo terminó un registro o una baja. */
	public enum ResultadoRegistro {
		/** Registro nuevo: Apple espera 201. */
		CREADO,
		/** Ya existía o se dio de baja: Apple espera 200. */
		ACTUALIZADO,
		/** El token de autenticación no es el de ese pase: 401. */
		NO_AUTORIZADO,
		/** El pase no existe o el servicio está apagado: 404. */
		NO_ENCONTRADO
	}

	/**
	 * @param etiqueta
	 *            la marca que Apple devuelve en {@code passesUpdatedSince}
	 */
	public record SerialesActualizados(List<String> serialNumbers, String etiqueta) {
	}

	/** Lo que responde {@link #pase}. */
	public sealed interface RespuestaPase {

		/** El pase vigente, con la hora de su última modificación. */
		record Entrega(byte[] pkpass, Instant modificado) implements RespuestaPase {
		}

		/** Por qué no hay pase que entregar. */
		enum Sin implements RespuestaPase {
			NO_ENCONTRADO, NO_AUTORIZADO, NO_MODIFICADO
		}
	}

	/** Un dispositivo guarda el pase: Apple manda su push token. */
	@Transactional
	public ResultadoRegistro registrar(String dispositivo, String passTypeId, String serial, String token,
			String pushToken) {
		Validacion validacion = validar(passTypeId, serial, token);
		if (validacion.error() != null) {
			return validacion.error();
		}
		if (dispositivo == null || dispositivo.isBlank() || dispositivo.length() > MAXIMO_DISPOSITIVO
				|| pushToken == null || pushToken.isBlank() || pushToken.length() > MAXIMO_PUSH_TOKEN) {
			throw new IllegalArgumentException("El dispositivo y el pushToken son obligatorios y de largo acotado");
		}
		if (!citas.existsById(validacion.citaId())) {
			return ResultadoRegistro.NO_ENCONTRADO;
		}
		Optional<RegistroPaseApple> existente = registros.findByDispositivoAndPassTypeIdAndSerial(dispositivo,
				passTypeId, serial);
		if (existente.isPresent()) {
			existente.get().cambiarPushToken(pushToken);
			return ResultadoRegistro.ACTUALIZADO;
		}
		registros.save(new RegistroPaseApple(dispositivo, pushToken, passTypeId, serial, validacion.citaId()));
		return ResultadoRegistro.CREADO;
	}

	/**
	 * El dispositivo borró el pase. Es idempotente: sin registro también es 200.
	 */
	@Transactional
	public ResultadoRegistro darDeBaja(String dispositivo, String passTypeId, String serial, String token) {
		Validacion validacion = validar(passTypeId, serial, token);
		if (validacion.error() != null) {
			return validacion.error();
		}
		registros.borrar(dispositivo, passTypeId, serial);
		return ResultadoRegistro.ACTUALIZADO;
	}

	/**
	 * Los pases de ese dispositivo que cambiaron después de la etiqueta.
	 *
	 * @param etiqueta
	 *            el {@code passesUpdatedSince} de Apple; nula o ilegible devuelve
	 *            todos
	 * @return vacío si no hay cambios (Apple espera 204)
	 */
	@Transactional(readOnly = true)
	public Optional<SerialesActualizados> serialesActualizados(String dispositivo, String passTypeId, String etiqueta) {
		if (!configuracion.appleActualizable() || !configuracion.applePassTypeId().equals(passTypeId)
				|| dispositivo == null || dispositivo.length() > MAXIMO_DISPOSITIVO) {
			return Optional.empty();
		}
		long desde = etiquetaAMilisegundos(etiqueta);
		List<Cita> cambiadas = registros.citasDelDispositivo(dispositivo, passTypeId).stream()
				.filter(cita -> cita.getUpdatedAt().toEpochMilli() > desde).toList();
		if (cambiadas.isEmpty()) {
			return Optional.empty();
		}
		long masReciente = cambiadas.stream().map(cita -> cita.getUpdatedAt().toEpochMilli())
				.max(Comparator.naturalOrder()).orElse(desde);
		List<String> seriales = cambiadas.stream().map(cita -> PREFIJO_SERIAL + cita.getId()).sorted().toList();
		return Optional.of(new SerialesActualizados(seriales, Long.toString(masReciente)));
	}

	/**
	 * El pase vigente de la cita.
	 *
	 * @param modificadoDesde
	 *            el {@code If-Modified-Since} del dispositivo, o nulo
	 */
	@Transactional(readOnly = true)
	public RespuestaPase pase(String passTypeId, String serial, String token, Instant modificadoDesde) {
		Validacion validacion = validar(passTypeId, serial, token);
		if (validacion.error() == ResultadoRegistro.NO_AUTORIZADO) {
			return RespuestaPase.Sin.NO_AUTORIZADO;
		}
		if (validacion.error() != null) {
			return RespuestaPase.Sin.NO_ENCONTRADO;
		}
		Optional<Cita> encontrada = citas.findById(validacion.citaId());
		if (encontrada.isEmpty()) {
			return RespuestaPase.Sin.NO_ENCONTRADO;
		}
		Cita cita = encontrada.get();
		Instant modificado = cita.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS);
		if (modificadoDesde != null && !modificado.isAfter(modificadoDesde)) {
			return RespuestaPase.Sin.NO_MODIFICADO;
		}
		String nombre = vehiculos.buscarPorId(cita.getVehiculoClienteId())
				.filter(propio -> propio.propietario().id().equals(cita.getClienteId())).map(CitaApp::describir)
				.orElse(null);
		DatosPase datos = new DatosPase(cita.getId(), firmaCitas.contenidoQr(cita.getId()),
				ZonedDateTime.of(cita.getFecha(), cita.getHoraInicio(), AgendaAppService.ZONA_AGENCIA),
				ZonedDateTime.of(cita.getFecha(), cita.getHoraFin(), AgendaAppService.ZONA_AGENCIA), nombre);
		byte[] pkpass = generador.generar(datos, cita.getEstado() == EstadoCita.CANCELADA);
		return new RespuestaPase.Entrega(pkpass, modificado);
	}

	/**
	 * Los errores que reportan los dispositivos. Sin datos personales: se acota el
	 * largo y se quitan saltos de línea para que nadie falsifique el registro.
	 */
	public void registrarLog(List<String> mensajes) {
		if (mensajes == null) {
			return;
		}
		mensajes.stream().limit(MAXIMO_LOGS).forEach(mensaje -> {
			String limpio = mensaje == null ? "" : mensaje.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
			LOG.info("Registro de un dispositivo de Wallet: {}",
					limpio.substring(0, Math.min(limpio.length(), MAXIMO_LARGO_LOG)));
		});
	}

	private Validacion validar(String passTypeId, String serial, String token) {
		if (!configuracion.appleActualizable() || !configuracion.applePassTypeId().equals(passTypeId) || serial == null
				|| !serial.startsWith(PREFIJO_SERIAL)) {
			return new Validacion(ResultadoRegistro.NO_ENCONTRADO, null);
		}
		long citaId;
		try {
			citaId = Long.parseLong(serial.substring(PREFIJO_SERIAL.length()));
		} catch (NumberFormatException noEsNumero) {
			return new Validacion(ResultadoRegistro.NO_ENCONTRADO, null);
		}
		if (!tokens.valido(citaId, token)) {
			return new Validacion(ResultadoRegistro.NO_AUTORIZADO, null);
		}
		return new Validacion(null, citaId);
	}

	private static long etiquetaAMilisegundos(String etiqueta) {
		if (etiqueta == null || etiqueta.isBlank()) {
			return Long.MIN_VALUE;
		}
		try {
			return Long.parseLong(etiqueta.trim());
		} catch (NumberFormatException ilegible) {
			return Long.MIN_VALUE;
		}
	}

	private record Validacion(ResultadoRegistro error, Long citaId) {
	}
}
