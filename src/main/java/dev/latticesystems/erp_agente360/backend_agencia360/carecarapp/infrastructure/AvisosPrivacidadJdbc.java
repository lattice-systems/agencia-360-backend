package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisosPrivacidad;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code aviso_privacidad} y {@code aceptacion_aviso} con JDBC. */
@Repository
public class AvisosPrivacidadJdbc implements AvisosPrivacidad {

	private final JdbcTemplate jdbc;

	public AvisosPrivacidadJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Aviso> vigente(Instant ahora) {
		return jdbc.query("select * from aviso_privacidad where vigente_desde <= ? order by version desc limit 1",
				AvisosPrivacidadJdbc::leerAviso, Timestamp.from(ahora)).stream().findFirst();
	}

	@Override
	public Optional<Aviso> buscar(long id) {
		return jdbc.query("select * from aviso_privacidad where id = ?", AvisosPrivacidadJdbc::leerAviso, id).stream()
				.findFirst();
	}

	@Override
	public Optional<Aviso> ultima() {
		return jdbc
				.query("select * from aviso_privacidad order by version desc limit 1", AvisosPrivacidadJdbc::leerAviso)
				.stream().findFirst();
	}

	@Override
	public Aviso publicar(String texto, Instant vigenteDesde) {
		return jdbc.queryForObject("""
				insert into aviso_privacidad (version, texto, vigente_desde, created_at)
				select coalesce(max(version), 0) + 1, ?, ?, now() from aviso_privacidad
				returning *
				""", AvisosPrivacidadJdbc::leerAviso, texto, Timestamp.from(vigenteDesde));
	}

	@Override
	public Optional<Aceptacion> aceptacionDe(long clienteId, long avisoId) {
		return jdbc.query("""
				select a.aviso_privacidad_id, v.version, a.aceptado_en
				from aceptacion_aviso a
				join cuenta_app c on c.id = a.cuenta_app_id
				join aviso_privacidad v on v.id = a.aviso_privacidad_id
				where c.cliente_id = ? and a.aviso_privacidad_id = ?
				""", AvisosPrivacidadJdbc::leerAceptacion, clienteId, avisoId).stream().findFirst();
	}

	@Override
	public Aceptacion aceptar(long clienteId, long avisoId, Instant ahora) {
		// Dos toques seguidos no duplican: la restricción única lo impide y la
		// segunda inserción no hace nada.
		jdbc.update("""
				insert into aceptacion_aviso (cuenta_app_id, aviso_privacidad_id, aceptado_en)
				select c.id, ?, ? from cuenta_app c where c.cliente_id = ?
				on conflict (cuenta_app_id, aviso_privacidad_id) do nothing
				""", avisoId, Timestamp.from(ahora), clienteId);
		return aceptacionDe(clienteId, avisoId)
				.orElseThrow(() -> new IllegalStateException("El cliente " + clienteId + " no tiene cuenta de la app"));
	}

	private static Aviso leerAviso(ResultSet f, int i) throws SQLException {
		return new Aviso(f.getLong("id"), f.getInt("version"), f.getString("texto"),
				f.getTimestamp("vigente_desde").toInstant());
	}

	private static Aceptacion leerAceptacion(ResultSet f, int i) throws SQLException {
		return new Aceptacion(f.getLong("aviso_privacidad_id"), f.getInt("version"),
				f.getTimestamp("aceptado_en").toInstant());
	}
}
