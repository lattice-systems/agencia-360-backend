package dev.latticesystems.erp_agente360.backend_agencia360.archivos.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.Archivo;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.application.Deposito;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.archivos.almacen", havingValue = "base", matchIfMissing = true)
class DepositoEnBase implements Deposito {

	private final JdbcTemplate jdbc;

	DepositoEnBase(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(String clave, String tipoContenido, byte[] contenido) {
		jdbc.update("insert into archivo (clave, tipo_contenido, tamano_bytes, contenido) values (?, ?, ?, ?)", clave,
				tipoContenido, contenido.length, contenido);
	}

	@Override
	public Optional<Archivo> leer(String clave) {
		return jdbc.query("select tipo_contenido, contenido from archivo where clave = ?",
				(rs, i) -> new Archivo(clave, rs.getString(1), rs.getBytes(2)), clave).stream().findFirst();
	}

	@Override
	public void borrar(String clave) {
		jdbc.update("delete from archivo where clave = ?", clave);
	}
}
