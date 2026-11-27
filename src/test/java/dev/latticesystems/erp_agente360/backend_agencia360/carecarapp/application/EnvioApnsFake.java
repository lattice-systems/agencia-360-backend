package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EnvioApns} de pruebas: anota cada push que se le pide y responde lo
 * que la prueba disponga, sin red ni certificado de Apple.
 */
public class EnvioApnsFake implements EnvioApns {

	private final List<PushApns> enviados = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, ResultadoApns> resultadosPorToken = new HashMap<>();
	private boolean disponible = true;

	public void disponible(boolean disponible) {
		this.disponible = disponible;
	}

	/** Hace que los pushes a ese token reciban este resultado. */
	public void responderCon(String token, ResultadoApns resultado) {
		resultadosPorToken.put(token, resultado);
	}

	public List<PushApns> enviados() {
		return List.copyOf(enviados);
	}

	public void limpiar() {
		enviados.clear();
		resultadosPorToken.clear();
		disponible = true;
	}

	@Override
	public boolean disponible() {
		return disponible;
	}

	@Override
	public ResultadoApns enviar(PushApns push) {
		enviados.add(push);
		return resultadosPorToken.getOrDefault(push.token(), new ResultadoApns(200, ""));
	}
}
