package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones;

import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.EnvioPush;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link EnvioPush} de pruebas: guarda lo que se «enviaría» y contesta lo que
 * la prueba le pida, sin red ni credenciales de FCM.
 */
public class EnvioPushFake implements EnvioPush {

	private final List<MensajePush> enviados = new CopyOnWriteArrayList<>();
	private volatile ResultadoEnvio resultado = ResultadoEnvio.ENVIADO;
	private volatile boolean lanza;

	@Override
	public ResultadoEnvio enviar(MensajePush mensaje) {
		enviados.add(mensaje);
		if (lanza) {
			throw new IllegalStateException("FCM caído (simulado)");
		}
		return resultado;
	}

	public List<MensajePush> enviados() {
		return List.copyOf(enviados);
	}

	public void responder(ResultadoEnvio nuevo) {
		this.resultado = nuevo;
	}

	public void lanzarExcepcion(boolean lanza) {
		this.lanza = lanza;
	}

	public void reiniciar() {
		enviados.clear();
		resultado = ResultadoEnvio.ENVIADO;
		lanza = false;
	}
}
