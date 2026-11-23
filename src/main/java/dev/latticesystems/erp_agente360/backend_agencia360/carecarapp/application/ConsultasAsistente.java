package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.time.Instant;
import java.util.List;

/** Historial de preguntas al asistente ({@code consulta_asistente}). */
public interface ConsultasAsistente {

	/** Una página citada en una respuesta. */
	record Fuente(long manualId, int pagina) {
	}

	/** Lo que se guarda de cada pregunta. */
	record NuevaConsulta(long clienteId, long vehiculoClienteId, String pregunta, String respuesta,
			List<Fuente> fuentes, boolean encontrado, String proveedor) {
	}

	/** @return el id de la consulta */
	long guardar(NuevaConsulta consulta);

	long contarDelCliente(long clienteId, Instant desde);

	long contarTodas(Instant desde);
}
