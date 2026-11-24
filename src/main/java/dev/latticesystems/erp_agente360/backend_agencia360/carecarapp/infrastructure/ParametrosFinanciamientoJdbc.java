package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ParametrosFinanciamiento;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code parametro_financiamiento}: siempre la fila más reciente. */
@Repository
public class ParametrosFinanciamientoJdbc implements ParametrosFinanciamiento.Fuente {

	private final JdbcTemplate jdbc;

	public ParametrosFinanciamientoJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ParametrosFinanciamiento actuales() {
		return jdbc.queryForObject("""
				select tasa_anual, enganche_minimo_pct, plazos from parametro_financiamiento
				order by updated_at desc, id desc limit 1
				""", (fila, i) -> new ParametrosFinanciamiento(fila.getBigDecimal("tasa_anual"),
				fila.getBigDecimal("enganche_minimo_pct"),
				Arrays.stream(fila.getString("plazos").split(",")).map(String::strip).map(Integer::valueOf).toList()));
	}
}
