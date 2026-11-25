package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ArchivosArco;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code archivo_arco} con JDBC. */
@Repository
public class ArchivosArcoJdbc implements ArchivosArco {

	private final JdbcTemplate jdbc;

	public ArchivosArcoJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(long solicitudId, byte[] contenido) {
		jdbc.update("insert into archivo_arco (solicitud_arco_id, contenido, tamano_bytes) values (?, ?, ?)",
				solicitudId, contenido, contenido.length);
	}

	@Override
	public Optional<byte[]> leer(long solicitudId) {
		return jdbc.query("select contenido from archivo_arco where solicitud_arco_id = ?",
				(f, i) -> f.getBytes("contenido"), solicitudId).stream().findFirst();
	}

	@Override
	public void borrar(long solicitudId) {
		jdbc.update("delete from archivo_arco where solicitud_arco_id = ?", solicitudId);
	}
}
