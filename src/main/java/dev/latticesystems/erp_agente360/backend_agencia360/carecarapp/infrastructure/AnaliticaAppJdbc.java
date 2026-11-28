package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnaliticaAppJdbc {

	private final JdbcTemplate jdbc;

	public AnaliticaAppJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void registrarPaso(UUID intento, int etapa, Long cita, long cliente) {
		if (cita != null && !Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists(select 1 from cita where id = ? and cliente_id = ?)", Boolean.class, cita, cliente))) {
			throw new RecursoNoEncontradoException("No existe la cita");
		}
		jdbc.update("""
				insert into embudo_cita_analitica (intento, etapa, cita_id) values (?, ?, ?)
				on conflict (intento) do update set
				    etapa = greatest(embudo_cita_analitica.etapa, excluded.etapa),
				    cita_id = coalesce(embudo_cita_analitica.cita_id, excluded.cita_id)
				""", intento, etapa, cita);
	}

	public void sumarDiagnostico(int flutter, int asincrono, int crashesAndroid, int muestras, int arranque,
			int primera, int cuadros, int perdidos) {
		jdbc.update("""
				insert into diagnostico_app_diario (dia, errores_flutter, errores_asincronos, crashes_android, muestras,
				    arranque_ms, primera_pantalla_ms, cuadros_medidos, cuadros_perdidos)
				values ((now() at time zone 'UTC')::date, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (dia) do update set
				    errores_flutter = diagnostico_app_diario.errores_flutter + excluded.errores_flutter,
				    errores_asincronos = diagnostico_app_diario.errores_asincronos + excluded.errores_asincronos,
				    crashes_android = diagnostico_app_diario.crashes_android + excluded.crashes_android,
				    muestras = diagnostico_app_diario.muestras + excluded.muestras,
				    arranque_ms = diagnostico_app_diario.arranque_ms + excluded.arranque_ms,
				    primera_pantalla_ms = diagnostico_app_diario.primera_pantalla_ms + excluded.primera_pantalla_ms,
				    cuadros_medidos = diagnostico_app_diario.cuadros_medidos + excluded.cuadros_medidos,
				    cuadros_perdidos = diagnostico_app_diario.cuadros_perdidos + excluded.cuadros_perdidos
				""", flutter, asincrono, crashesAndroid, muestras, arranque, primera, cuadros, perdidos);
	}

	public Embudo embudo() {
		return jdbc.queryForObject("""
				select count(*) vistos, count(*) filter (where e.etapa >= 2) elegidos,
				    count(distinct c.id) confirmados,
				    count(distinct c.id) filter (where c.estado = 'ATENDIDA' or exists (
				        select 1 from orden_servicio o where o.cita_id = c.id and o.fecha_cierre is not null
				    )) atendidos
				from embudo_cita_analitica e left join cita c on c.id = e.cita_id
				where e.iniciado_en >= now() - interval '30 days'
				""", (rs, indice) -> new Embudo(rs.getLong("vistos"), rs.getLong("elegidos"), rs.getLong("confirmados"),
				rs.getLong("atendidos")));
	}

	public Diagnostico diagnostico() {
		return jdbc.queryForObject("""
				select coalesce(sum(errores_flutter), 0) flutter, coalesce(sum(errores_asincronos), 0) asincronos,
				    coalesce(sum(crashes_android), 0) crashes_android,
				    coalesce(sum(muestras), 0) muestras, coalesce(sum(arranque_ms), 0) arranque,
				    coalesce(sum(primera_pantalla_ms), 0) primera, coalesce(sum(cuadros_medidos), 0) cuadros,
				    coalesce(sum(cuadros_perdidos), 0) perdidos
				from diagnostico_app_diario where dia >= (now() at time zone 'UTC')::date - 29
				""",
				(rs, indice) -> new Diagnostico(rs.getLong("flutter"), rs.getLong("asincronos"),
						rs.getLong("crashes_android"), rs.getLong("muestras"), rs.getLong("arranque"),
						rs.getLong("primera"), rs.getLong("cuadros"), rs.getLong("perdidos")));
	}

	public long solicitudesEstreno() {
		return jdbc.queryForObject(
				"select count(*) from solicitud_estreno where created_at >= now() - interval '30 days'", Long.class);
	}

	public long ordenesEnVivo() {
		return jdbc.queryForObject("""
				select count(*) from orden_servicio o where o.fecha_cierre is null
				and exists (select 1 from etapa_orden e where e.orden_servicio_id = o.id)
				""", Long.class);
	}

	public record Embudo(long vistos, long elegidos, long confirmados, long atendidos) {
	}

	public record Diagnostico(long flutter, long asincronos, long crashesAndroid, long muestras, long arranque,
			long primera, long cuadros, long perdidos) {
	}
}
