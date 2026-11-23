package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ConsultasAsistente;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** {@code consulta_asistente} con JDBC; {@code fuentes} es jsonb. */
@Repository
public class ConsultasAsistenteJdbc implements ConsultasAsistente {

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;

	public ConsultasAsistenteJdbc(JdbcTemplate jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public long guardar(NuevaConsulta consulta) {
		String fuentes;
		try {
			fuentes = json.writeValueAsString(consulta.fuentes());
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("No se pudieron guardar las fuentes", ex);
		}
		GeneratedKeyHolder llave = new GeneratedKeyHolder();
		jdbc.update(conexion -> {
			PreparedStatement sentencia = conexion.prepareStatement("""
					insert into consulta_asistente
					  (cliente_id, vehiculo_cliente_id, pregunta, respuesta, fuentes, encontrado, proveedor)
					values (?, ?, ?, ?, ?::jsonb, ?, ?)
					""", new String[]{"id"});
			sentencia.setLong(1, consulta.clienteId());
			sentencia.setLong(2, consulta.vehiculoClienteId());
			sentencia.setString(3, consulta.pregunta());
			sentencia.setString(4, consulta.respuesta());
			sentencia.setString(5, fuentes);
			sentencia.setBoolean(6, consulta.encontrado());
			sentencia.setString(7, consulta.proveedor());
			return sentencia;
		}, llave);
		return Objects.requireNonNull(llave.getKey()).longValue();
	}

	@Override
	public long contarDelCliente(long clienteId, Instant desde) {
		Long total = jdbc.queryForObject(
				"select count(*) from consulta_asistente where cliente_id = ? and created_at >= ?", Long.class,
				clienteId, Timestamp.from(desde));
		return total == null ? 0 : total;
	}

	@Override
	public long contarTodas(Instant desde) {
		Long total = jdbc.queryForObject("select count(*) from consulta_asistente where created_at >= ?", Long.class,
				Timestamp.from(desde));
		return total == null ? 0 : total;
	}
}
