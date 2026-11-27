package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application;

import java.util.Map;
import java.util.Objects;

/**
 * El canal que lleva un push al teléfono (V2-M06.07). La implementación real
 * habla con FCM; las pruebas usan un fake que guarda lo que se enviaría.
 */
public interface EnvioPush {

	/**
	 * Manda un push a un dispositivo. No lanza: el resultado dice qué pasó.
	 */
	ResultadoEnvio enviar(MensajePush mensaje);

	/**
	 * @param token
	 *            token FCM del dispositivo
	 * @param datos
	 *            pares texto/texto que la app recibe sin mostrarlos; solo ids y
	 *            ruta, nunca datos personales
	 */
	record MensajePush(String token, String titulo, String cuerpo, Map<String, String> datos) {

		public MensajePush {
			Objects.requireNonNull(token, "token");
			Objects.requireNonNull(titulo, "titulo");
			Objects.requireNonNull(cuerpo, "cuerpo");
			datos = Map.copyOf(datos);
		}
	}

	enum ResultadoEnvio {
		/** FCM lo aceptó. */
		ENVIADO,
		/** FCM dice que el token ya no existe: hay que desactivar el dispositivo. */
		TOKEN_INVALIDO,
		/** No hay credenciales: no se intentó nada. */
		NO_CONFIGURADO,
		/** Cualquier otro fallo (red, 5xx, cuota); se registra y se sigue. */
		FALLO
	}
}
