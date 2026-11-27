package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.Bandeja;
import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.ContenidoAviso;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Bandeja} sobre la tabla {@code notificacion} (V2-M06.07). Escribe en
 * su propia transacción: se llama después de confirmar la que causó el aviso.
 */
@Repository
class BandejaJdbc implements Bandeja {

	private final JdbcTemplate jdbc;
	private final ObjectMapper json = new ObjectMapper();

	BandejaJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void guardar(Long clienteId, ContenidoAviso contenido, String evento, Map<String, String> datos) {
		jdbc.update("""
				insert into notificacion (cliente_id, categoria, titulo, cuerpo, destino, evento, datos)
				values (?, ?, ?, ?, ?, ?, ?::jsonb)
				""", clienteId, contenido.categoria(), contenido.titulo(), contenido.cuerpo(), contenido.destino(),
				evento, comoJson(datos));
	}

	private String comoJson(Map<String, String> datos) {
		try {
			return json.writeValueAsString(datos);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("No se pudo escribir los datos del aviso", ex);
		}
	}
}
