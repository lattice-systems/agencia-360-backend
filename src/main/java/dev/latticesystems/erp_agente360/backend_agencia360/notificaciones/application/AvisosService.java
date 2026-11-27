package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.EntregaDeAvisos;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.Dispositivos.DispositivoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.MensajePush;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush.ResultadoEnvio;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * El servicio único de envío (V2-M06.07): arma el aviso, lo deja en la bandeja
 * y lo manda por push a cada dispositivo activo del cliente.
 *
 * <p>
 * Nunca lanza. Se llama después de confirmar la transacción que causó el aviso,
 * y un fallo de FCM o de la base de avisos no puede deshacer una cita, un pago
 * o una orden. Cada paso falla por separado: si la bandeja no se puede guardar,
 * el push sale igual, y un dispositivo con error no frena a los demás.
 *
 * <p>
 * La bandeja se guarda en el momento (es un insert), pero el push sale en otro
 * hilo: FCM puede tardar segundos y no debe retener la petición del chofer o
 * del asesor que provocó el aviso. Si el servidor se apaga con un push en
 * vuelo, ese push se pierde; el aviso ya quedó en la bandeja.
 */
@Service
public class AvisosService implements EntregaDeAvisos {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosService.class);

	private final RedactorDeAvisos redactor;
	private final Dispositivos dispositivos;
	private final Bandeja bandeja;
	private final EnvioPush envio;
	private final Executor hilos;

	@Autowired
	public AvisosService(RedactorDeAvisos redactor, Dispositivos dispositivos, Bandeja bandeja, EnvioPush envio) {
		this(redactor, dispositivos, bandeja, envio, Executors.newVirtualThreadPerTaskExecutor());
	}

	/** Las pruebas pasan {@code Runnable::run} para que el envío sea síncrono. */
	AvisosService(RedactorDeAvisos redactor, Dispositivos dispositivos, Bandeja bandeja, EnvioPush envio,
			Executor hilos) {
		this.redactor = redactor;
		this.dispositivos = dispositivos;
		this.bandeja = bandeja;
		this.envio = envio;
		this.hilos = hilos;
	}

	@Override
	public void avisar(Long clienteId, String evento, Map<String, Object> datos) {
		try {
			Optional<ContenidoAviso> contenido = redactor.redactar(evento, datos);
			if (contenido.isEmpty()) {
				return;
			}
			Map<String, String> ids = ids(datos);
			guardar(clienteId, contenido.get(), evento, ids);
			hilos.execute(() -> mandar(clienteId, contenido.get(), evento, ids));
		} catch (RuntimeException fallo) {
			LOG.warn("No se pudo avisar al cliente {} del evento {}", clienteId, evento, fallo);
		}
	}

	private void guardar(Long clienteId, ContenidoAviso contenido, String evento, Map<String, String> ids) {
		try {
			bandeja.guardar(clienteId, contenido, evento, ids);
		} catch (RuntimeException fallo) {
			LOG.warn("No se pudo guardar en la bandeja el aviso {} del cliente {}", evento, clienteId, fallo);
		}
	}

	private void mandar(Long clienteId, ContenidoAviso contenido, String evento, Map<String, String> ids) {
		try {
			enviarADispositivos(clienteId, contenido, evento, ids);
		} catch (RuntimeException fallo) {
			LOG.warn("No se pudo mandar el push {} al cliente {}", evento, clienteId, fallo);
		}
	}

	private void enviarADispositivos(Long clienteId, ContenidoAviso contenido, String evento, Map<String, String> ids) {
		List<DispositivoDeCliente> activos = dispositivos.activosDe(clienteId);
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("evento", evento);
		payload.put("destino", contenido.destino());
		payload.putAll(ids);
		for (DispositivoDeCliente dispositivo : activos) {
			try {
				ResultadoEnvio resultado = envio.enviar(
						new MensajePush(dispositivo.tokenFcm(), contenido.titulo(), contenido.cuerpo(), payload));
				if (resultado == ResultadoEnvio.TOKEN_INVALIDO) {
					dispositivos.desactivar(dispositivo.id());
				}
			} catch (RuntimeException fallo) {
				LOG.warn("No se pudo mandar el push {} al dispositivo {}", evento, dispositivo.id(), fallo);
			}
		}
	}

	/**
	 * De los datos del evento solo viajan los ids del recurso (las claves que
	 * terminan en {@code Id}); lo demás puede ser un nombre, un monto o una fecha,
	 * que no deben salir por un canal de terceros.
	 */
	private Map<String, String> ids(Map<String, Object> datos) {
		Map<String, String> ids = new LinkedHashMap<>();
		datos.entrySet().stream().filter(dato -> dato.getKey().endsWith("Id") && dato.getValue() != null)
				.sorted(Map.Entry.comparingByKey()).forEach(dato -> ids.put(dato.getKey(), dato.getValue().toString()));
		return ids;
	}
}
