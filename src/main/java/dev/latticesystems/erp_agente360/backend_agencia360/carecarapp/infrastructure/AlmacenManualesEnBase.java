package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AlmacenManuales;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Guarda los PDF de los manuales en la tabla {@code archivo_manual}. Es el
 * almacén por omisión: en Azure Container Apps el disco del contenedor se borra
 * en cada revisión, y la base (Neon) sí se conserva, sin costo extra.
 */
@Component
@ConditionalOnProperty(name = "app.manuales.almacen", havingValue = "base", matchIfMissing = true)
public class AlmacenManualesEnBase implements AlmacenManuales {

	private static final Pattern CLAVE_VALIDA = Pattern.compile("[0-9a-f\\-]{36}\\.pdf");

	private final JdbcTemplate jdbc;

	public AlmacenManualesEnBase(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String guardar(byte[] contenido) {
		String clave = UUID.randomUUID() + ".pdf";
		jdbc.update("insert into archivo_manual (clave, contenido) values (?, ?)", clave, contenido);
		return clave;
	}

	@Override
	public Optional<byte[]> leer(String clave) {
		if (!CLAVE_VALIDA.matcher(clave).matches()) {
			return Optional.empty();
		}
		return jdbc.query("select contenido from archivo_manual where clave = ?", (rs, i) -> rs.getBytes(1), clave)
				.stream().findFirst();
	}

	@Override
	public void borrar(String clave) {
		if (CLAVE_VALIDA.matcher(clave).matches()) {
			jdbc.update("delete from archivo_manual where clave = ?", clave);
		}
	}
}
