package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.SolicitudesEstreno;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * {@code solicitud_estreno} con JDBC; el texto del modelo sale de
 * {@code modelo_catalogo}.
 */
@Repository
public class SolicitudesEstrenoJdbc implements SolicitudesEstreno {

	private static final String SELECT = """
			select s.*, trim(m.marca || ' ' || m.modelo || ' ' || m.anio || ' ' || coalesce(m.version, '')) as texto_modelo
			from solicitud_estreno s join modelo_catalogo m on m.id = s.modelo_catalogo_id
			""";

	private final JdbcTemplate jdbc;

	public SolicitudesEstrenoJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public long guardar(NuevaSolicitud s) {
		GeneratedKeyHolder llave = new GeneratedKeyHolder();
		jdbc.update(conexion -> {
			PreparedStatement p = conexion.prepareStatement("""
					insert into solicitud_estreno (cliente_id, modelo_catalogo_id, tipo, preferencia_fecha,
					  preferencia_franja, enganche, plazo_meses, mensualidad, tasa_anual, estimacion_toma_cuenta_id)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", new String[]{"id"});
			p.setLong(1, s.clienteId());
			p.setLong(2, s.modeloCatalogoId());
			p.setString(3, s.tipo());
			p.setObject(4, s.preferenciaFecha() == null ? null : Date.valueOf(s.preferenciaFecha()), Types.DATE);
			p.setString(5, s.preferenciaFranja());
			p.setBigDecimal(6, s.enganche());
			p.setObject(7, s.plazoMeses(), Types.INTEGER);
			p.setBigDecimal(8, s.mensualidad());
			p.setBigDecimal(9, s.tasaAnual());
			p.setObject(10, s.estimacionTomaCuentaId(), Types.BIGINT);
			return p;
		}, llave);
		return Objects.requireNonNull(llave.getKey()).longValue();
	}

	@Override
	public void ligarProspecto(long solicitudId, long prospectoId) {
		jdbc.update("update solicitud_estreno set prospecto_id = ? where id = ?", prospectoId, solicitudId);
	}

	@Override
	public Solicitud buscar(long id) {
		return jdbc.queryForObject(SELECT + " where s.id = ?", SolicitudesEstrenoJdbc::leer, id);
	}

	@Override
	public List<Solicitud> delCliente(long clienteId, int pagina, int tamano) {
		return jdbc.query(SELECT + " where s.cliente_id = ? order by s.created_at desc, s.id desc limit ? offset ?",
				SolicitudesEstrenoJdbc::leer, clienteId, tamano, (long) pagina * tamano);
	}

	@Override
	public long contarDelCliente(long clienteId) {
		Long total = jdbc.queryForObject("select count(*) from solicitud_estreno where cliente_id = ?", Long.class,
				clienteId);
		return total == null ? 0 : total;
	}

	@Override
	public List<Solicitud> iguales(long clienteId, long modeloCatalogoId, String tipo) {
		return jdbc.query(SELECT + " where s.cliente_id = ? and s.modelo_catalogo_id = ? and s.tipo = ?",
				SolicitudesEstrenoJdbc::leer, clienteId, modeloCatalogoId, tipo);
	}

	private static Solicitud leer(ResultSet f, int i) throws SQLException {
		Date fecha = f.getDate("preferencia_fecha");
		return new Solicitud(f.getLong("id"), f.getLong("modelo_catalogo_id"), f.getString("texto_modelo"),
				f.getString("tipo"), fecha == null ? null : fecha.toLocalDate(), f.getString("preferencia_franja"),
				f.getBigDecimal("enganche"), (Integer) f.getObject("plazo_meses"), f.getBigDecimal("mensualidad"),
				f.getBigDecimal("tasa_anual"), (Long) f.getObject("estimacion_toma_cuenta_id"),
				(Long) f.getObject("prospecto_id"), f.getTimestamp("created_at").toInstant());
	}
}
