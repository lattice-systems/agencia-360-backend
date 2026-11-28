package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncuestasAppService {

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private final JdbcTemplate jdbc;

	public EncuestasAppService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Pendiente> pendientes(Long clienteId) {
		return jdbc.query("""
				select o.id, o.fecha_cierre, v.modelo
				from orden_servicio o join vehiculo_cliente v on v.id = o.vehiculo_cliente_id
				left join encuesta_servicio e on e.orden_servicio_id = o.id
				where v.cliente_id = ? and o.estatus = 'ENTREGADO' and o.fecha_cierre is not null
				  and e.respondida_en is null
				  and (e.pospuesta_hasta is null or e.pospuesta_hasta <= now())
				order by o.fecha_cierre desc, o.id desc
				""", (rs, fila) -> new Pendiente(rs.getLong("id"), rs.getTimestamp("fecha_cierre").toInstant(),
				rs.getString("modelo")), clienteId);
	}

	@Transactional
	public void posponer(Long clienteId, Long ordenId) {
		garantizarEncuesta(clienteId, ordenId);
		int filas = jdbc.update("""
				update encuesta_servicio set pospuesta_hasta = now() + interval '24 hours'
				where orden_servicio_id = ? and cliente_id = ? and respondida_en is null
				""", ordenId, clienteId);
		exigirPendiente(filas);
	}

	@Transactional
	public void responder(Long clienteId, Long ordenId, Calificacion calificacion) {
		garantizarEncuesta(clienteId, ordenId);
		int filas = jdbc.update("""
				update encuesta_servicio e
				set nps = ?, tiempo = ?, trato = ?, limpieza = ?, calificacion_asesor = ?,
				    comentario = ?, respondida_en = now(), pospuesta_hasta = null,
				    asesor_id = o.asesor_id
				from orden_servicio o
				where e.orden_servicio_id = o.id and o.id = ? and e.cliente_id = ? and e.respondida_en is null
				""", calificacion.nps(), calificacion.tiempo(), calificacion.trato(), calificacion.limpieza(),
				calificacion.asesor(), calificacion.comentario(), ordenId, clienteId);
		exigirPendiente(filas);
	}

	@Transactional
	public void pedirContacto(Long clienteId, Long ordenId) {
		List<Integer> notas = jdbc.query("""
				select nps from encuesta_servicio
				where orden_servicio_id = ? and cliente_id = ? and respondida_en is not null
				""", (rs, fila) -> rs.getInt("nps"), ordenId, clienteId);
		if (notas.isEmpty()) {
			throw new RecursoNoEncontradoException("No existe una encuesta respondida para esa orden");
		}
		if (notas.getFirst() > 6) {
			throw new IllegalArgumentException("El contacto está disponible para calificaciones NPS de 0 a 6");
		}
		jdbc.update("update encuesta_servicio set pide_contacto = true where orden_servicio_id = ? and cliente_id = ?",
				ordenId, clienteId);
		jdbc.update("""
				insert into tarea_contacto_encuesta (encuesta_servicio_id, asesor_id)
				select id, asesor_id from encuesta_servicio where orden_servicio_id = ? and cliente_id = ?
				on conflict (encuesta_servicio_id) do nothing
				""", ordenId, clienteId);
	}

	private void garantizarEncuesta(Long clienteId, Long ordenId) {
		Boolean existe = jdbc.queryForObject("""
				select exists (
				    select 1 from orden_servicio o join vehiculo_cliente v on v.id = o.vehiculo_cliente_id
				    where o.id = ? and v.cliente_id = ? and o.estatus = 'ENTREGADO' and o.fecha_cierre is not null
				)
				""", Boolean.class, ordenId, clienteId);
		if (!Boolean.TRUE.equals(existe)) {
			throw new RecursoNoEncontradoException("No existe una orden cerrada para ese servicio");
		}
		jdbc.update("""
				insert into encuesta_servicio (orden_servicio_id, cliente_id) values (?, ?)
				on conflict (orden_servicio_id) do nothing
				""", ordenId, clienteId);
	}

	private static void exigirPendiente(int filas) {
		if (filas != 1) {
			throw new ConflictoException("La orden ya tiene una encuesta respondida");
		}
	}

	public List<Tarea> tareas(Long asesorId, boolean administracion) {
		return jdbc.query("""
				select t.id, e.orden_servicio_id, t.asesor_id, e.nps, t.creada_en
				from tarea_contacto_encuesta t join encuesta_servicio e on e.id = t.encuesta_servicio_id
				where t.cerrada_en is null and (? or t.asesor_id = ?)
				order by t.creada_en, t.id
				""", (rs, fila) -> new Tarea(rs.getLong("id"), rs.getLong("orden_servicio_id"),
				rs.getObject("asesor_id", Long.class), rs.getInt("nps"), rs.getTimestamp("creada_en").toInstant()),
				administracion, asesorId);
	}

	@Transactional
	public void registrarRespuesta(Long tareaId, Long asesorId, boolean administracion, String respuesta) {
		int filas = jdbc.update("""
				update tarea_contacto_encuesta set respuesta = ?, cerrada_en = now()
				where id = ? and cerrada_en is null and (? or asesor_id = ?)
				""", respuesta.strip(), tareaId, administracion, asesorId);
		if (filas == 0) {
			throw new RecursoNoEncontradoException("No existe una tarea de contacto pendiente");
		}
	}

	public long respuestasRegistradas(LocalDate desde, LocalDate hasta) {
		return jdbc.queryForObject("""
				select count(*) from tarea_contacto_encuesta
				where cerrada_en >= ? and cerrada_en < ? and respuesta is not null
				""", Long.class, desde.atStartOfDay(ZONA).toOffsetDateTime(),
				hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime());
	}

	public List<Agregado> agregados(LocalDate desde, LocalDate hasta) {
		if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) > 365) {
			throw new IllegalArgumentException("Selecciona un periodo de hasta 366 días");
		}
		OffsetDateTime inicio = desde.atStartOfDay(ZONA).toOffsetDateTime();
		OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime();
		return jdbc.query("""
				select asesor_id, count(*) total, count(*) filter (where nps >= 9) promotores,
				    count(*) filter (where nps <= 6) detractores
				from encuesta_servicio where respondida_en >= ? and respondida_en < ?
				group by asesor_id order by asesor_id nulls last
				""", (rs, fila) -> {
			long total = rs.getLong("total");
			long promotores = rs.getLong("promotores");
			long detractores = rs.getLong("detractores");
			return new Agregado(rs.getObject("asesor_id", Long.class), total, promotores, detractores,
					Math.round(100.0 * (promotores - detractores) / total));
		}, inicio, fin);
	}

	public record Pendiente(Long ordenId, Instant cerradaEn, String modelo) {
	}

	public record Calificacion(int nps, int tiempo, int trato, int limpieza, int asesor, String comentario) {
	}

	public record Tarea(Long id, Long ordenId, Long asesorId, int nps, Instant creadaEn) {
	}

	public record Agregado(Long asesorId, long total, long promotores, long detractores, long nps) {
	}
}
