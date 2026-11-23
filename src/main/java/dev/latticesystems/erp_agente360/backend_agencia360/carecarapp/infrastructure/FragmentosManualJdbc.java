package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.FragmentosManual;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * {@code fragmento_manual} con JDBC: la columna {@code embedding} es de
 * pgvector y se escribe y compara como texto {@code [0.1,0.2,...]} con
 * {@code ::vector}.
 */
@Repository
public class FragmentosManualJdbc implements FragmentosManual {

	private static final RowMapper<Fragmento> FRAGMENTO = (rs, i) -> new Fragmento(rs.getLong("manual_id"),
			rs.getInt("pagina"), rs.getString("texto"));

	private final JdbcTemplate jdbc;

	public FragmentosManualJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void reemplazar(long manualId, List<Fragmento> paginas) {
		for (Fragmento pagina : paginas) {
			jdbc.update("""
					insert into fragmento_manual (manual_id, pagina, texto) values (?, ?, ?)
					on conflict (manual_id, pagina) do update
					set texto = excluded.texto,
					    embedding = case when fragmento_manual.texto = excluded.texto
					                     then fragmento_manual.embedding else null end
					""", manualId, pagina.pagina(), pagina.texto());
		}
		int ultima = paginas.stream().mapToInt(Fragmento::pagina).max().orElse(0);
		jdbc.update("delete from fragmento_manual where manual_id = ? and pagina > ?", manualId, ultima);
	}

	@Override
	public boolean tieneFragmentos(long manualId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from fragmento_manual where manual_id = ?)", Boolean.class, manualId));
	}

	@Override
	public List<Long> manualesSinFragmentos() {
		return jdbc.queryForList("""
				select m.id from manual m
				where not exists (select 1 from fragmento_manual f where f.manual_id = m.id)
				order by m.id
				""", Long.class);
	}

	@Override
	public List<Pendiente> sinVector(int limite) {
		return jdbc.query("select id, texto from fragmento_manual where embedding is null order by id limit ?",
				(rs, i) -> new Pendiente(rs.getLong("id"), rs.getString("texto")), limite);
	}

	@Override
	public void ponerVector(long fragmentoId, float[] vector) {
		jdbc.update("update fragmento_manual set embedding = ?::vector where id = ?", comoTexto(vector), fragmentoId);
	}

	@Override
	public boolean tieneVectores(long manualId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from fragmento_manual where manual_id = ? and embedding is not null)",
				Boolean.class, manualId));
	}

	@Override
	public List<Fragmento> masParecidos(long manualId, float[] vector, int limite) {
		return jdbc.query("""
				select manual_id, pagina, texto from fragmento_manual
				where manual_id = ? and embedding is not null
				order by embedding <=> ?::vector
				limit ?
				""", FRAGMENTO, manualId, comoTexto(vector), limite);
	}

	@Override
	public List<Fragmento> porPalabras(long manualId, String texto, int limite) {
		String consulta = Arrays.stream(texto.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
				.filter(palabra -> palabra.length() > 2).distinct().collect(Collectors.joining(" | "));
		if (consulta.isBlank()) {
			return List.of();
		}
		return jdbc.query("""
				select manual_id, pagina, texto from fragmento_manual
				where manual_id = ? and to_tsvector('spanish', texto) @@ to_tsquery('spanish', ?)
				order by ts_rank(to_tsvector('spanish', texto), to_tsquery('spanish', ?)) desc, pagina
				limit ?
				""", FRAGMENTO, manualId, consulta, consulta, limite);
	}

	private static String comoTexto(float[] vector) {
		StringBuilder texto = new StringBuilder("[");
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				texto.append(',');
			}
			texto.append(vector[i]);
		}
		return texto.append(']').toString();
	}
}
