package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudesArco;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code solicitud_arco} con JDBC. */
@Repository
public class SolicitudesArcoJdbc implements SolicitudesArco {

	private final JdbcTemplate jdbc;

	public SolicitudesArcoJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Solicitud> crear(long clienteId, String tipo, Instant ahora) {
		// Si ya hay una pendiente de ese tipo, el índice único parcial hace que no
		// se inserte nada y no haya excepción que estropee la transacción.
		return jdbc.query("""
				insert into solicitud_arco (cliente_id, tipo, estado, creada_en)
				values (?, ?, 'PENDIENTE', ?)
				on conflict (cliente_id, tipo) where estado = 'PENDIENTE' do nothing
				returning *
				""", SolicitudesArcoJdbc::leer, clienteId, tipo, Timestamp.from(ahora)).stream().findFirst();
	}

	@Override
	public Optional<Solicitud> buscar(long id) {
		return jdbc.query("select * from solicitud_arco where id = ?", SolicitudesArcoJdbc::leer, id).stream()
				.findFirst();
	}

	@Override
	public Optional<Solicitud> pendienteDe(long clienteId, String tipo) {
		return jdbc.query("select * from solicitud_arco where cliente_id = ? and tipo = ? and estado = 'PENDIENTE'",
				SolicitudesArcoJdbc::leer, clienteId, tipo).stream().findFirst();
	}

	@Override
	public List<Solicitud> delCliente(long clienteId, int limite) {
		return jdbc.query("select * from solicitud_arco where cliente_id = ? order by creada_en desc, id desc limit ?",
				SolicitudesArcoJdbc::leer, clienteId, limite);
	}

	@Override
	public List<Solicitud> conArchivo(long clienteId, String tipo) {
		return jdbc.query("select * from solicitud_arco where cliente_id = ? and tipo = ? and archivo_url is not null",
				SolicitudesArcoJdbc::leer, clienteId, tipo);
	}

	@Override
	public List<Solicitud> conArchivoResueltasAntesDe(Instant limite) {
		return jdbc.query("select * from solicitud_arco where archivo_url is not null and resuelta_en <= ?",
				SolicitudesArcoJdbc::leer, Timestamp.from(limite));
	}

	@Override
	public void resolver(long id, String archivoUrl, Instant ahora) {
		jdbc.update("""
				update solicitud_arco set estado = 'RESUELTA', archivo_url = ?, resuelta_en = ?
				where id = ? and estado = 'PENDIENTE'
				""", archivoUrl, Timestamp.from(ahora), id);
	}

	@Override
	public void fallar(long id, Instant ahora) {
		jdbc.update(
				"update solicitud_arco set estado = 'FALLIDA', resuelta_en = ? where id = ? and estado = 'PENDIENTE'",
				Timestamp.from(ahora), id);
	}

	@Override
	public void quitarArchivo(long id) {
		jdbc.update("update solicitud_arco set archivo_url = null where id = ?", id);
	}

	private static Solicitud leer(ResultSet f, int i) throws SQLException {
		Timestamp resuelta = f.getTimestamp("resuelta_en");
		return new Solicitud(f.getLong("id"), f.getLong("cliente_id"), f.getString("tipo"), f.getString("estado"),
				f.getString("archivo_url"), f.getTimestamp("creada_en").toInstant(),
				resuelta == null ? null : resuelta.toInstant());
	}
}
