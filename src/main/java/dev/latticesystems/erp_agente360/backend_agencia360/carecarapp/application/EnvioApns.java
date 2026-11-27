package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.Objects;
import java.util.Set;

/**
 * Salida hacia el servicio de notificaciones push de Apple (APNs) para los
 * pases de Wallet (V2-M25.06) y las Live Activities (V2-M25.05). Existe para
 * que quien manda el aviso se pruebe sin red ni certificado: la implementación
 * real es {@link EnvioApnsReal} y las pruebas usan una falsa.
 */
public interface EnvioApns {

	/**
	 * @return si hay credenciales para mandar; sin ellas {@link #enviar} no hace
	 *         nada
	 */
	boolean disponible();

	/**
	 * Manda un push. Nunca lanza: un fallo de red o de Apple se devuelve como
	 * resultado para que el aviso jamás rompa quien lo pidió.
	 */
	ResultadoApns enviar(PushApns push);

	/**
	 * Cómo viaja el push; fija el encabezado {@code apns-push-type} y la prioridad.
	 */
	enum TipoPush {
		/** Despierta Wallet en silencio para que baje el pase nuevo (prioridad 5). */
		BACKGROUND("background", "5"),
		/** Actualiza o termina una Live Activity (prioridad 10). */
		LIVE_ACTIVITY("liveactivity", "10");

		private final String encabezado;
		private final String prioridad;

		TipoPush(String encabezado, String prioridad) {
			this.encabezado = encabezado;
			this.prioridad = prioridad;
		}

		public String encabezado() {
			return encabezado;
		}

		public String prioridad() {
			return prioridad;
		}
	}

	/**
	 * @param token
	 *            token del dispositivo (o de la actividad) que dio Apple
	 * @param topic
	 *            valor de {@code apns-topic}
	 * @param carga
	 *            el JSON del push
	 * @param sandbox
	 *            si va al servidor de pruebas de Apple
	 */
	record PushApns(String token, String topic, TipoPush tipo, String carga, boolean sandbox) {

		public PushApns {
			Objects.requireNonNull(token, "token");
			Objects.requireNonNull(topic, "topic");
			Objects.requireNonNull(tipo, "tipo");
			Objects.requireNonNull(carga, "carga");
		}

		/** Push vacío que le dice a Wallet que pida el pase actualizado. */
		public static PushApns deUnPase(String pushToken, String passTypeId) {
			return new PushApns(pushToken, passTypeId, TipoPush.BACKGROUND, "{}", false);
		}

		/** Push que actualiza o termina una Live Activity de la app. */
		public static PushApns deUnaActividad(String pushToken, String bundleId, boolean sandbox, String carga) {
			return new PushApns(pushToken, bundleId + ".push-type.liveactivity", TipoPush.LIVE_ACTIVITY, carga,
					sandbox);
		}
	}

	/**
	 * @param codigo
	 *            estado HTTP de Apple; 0 si no hubo respuesta
	 * @param motivo
	 *            el {@code reason} de Apple cuando rechaza; vacío si no hay
	 */
	record ResultadoApns(int codigo, String motivo) {

		/** No hay credenciales: no se mandó nada. */
		public static final ResultadoApns NO_CONFIGURADO = new ResultadoApns(0, "NoConfigurado");

		private static final Set<String> MOTIVOS_DE_TOKEN_MUERTO = Set.of("Unregistered", "BadDeviceToken");

		public boolean aceptado() {
			return codigo == 200;
		}

		/**
		 * El token ya no sirve (app desinstalada, pase borrado o actividad terminada).
		 */
		public boolean tokenMuerto() {
			return codigo == 410 || MOTIVOS_DE_TOKEN_MUERTO.contains(motivo);
		}
	}
}
