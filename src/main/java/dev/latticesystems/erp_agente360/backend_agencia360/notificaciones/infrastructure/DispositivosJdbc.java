package dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.notificaciones.application.Dispositivos;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Dispositivos} sobre {@code dispositivo_push} (V2-M06.02).
 *
 * <p>
 * Une con {@code cuenta_app} por SQL en lugar de pedirle la cuenta a
 * {@code carecarapp}: el módulo no puede depender de él (sería un ciclo) y la
 * ficha liga el dispositivo a la cuenta, no al cliente. Las operaciones que
 * corren tras confirmar otra transacción usan la suya propia: en ese punto la
 * original ya no admite escrituras.
 */
@Repository
class DispositivosJdbc implements Dispositivos {

	private final JdbcTemplate jdbc;

	DispositivosJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public boolean registrar(Long clienteId, String instalacionId, String tokenFcm, String plataforma, String idioma) {
		List<Long> cuentas = jdbc.queryForList("select id from cuenta_app where cliente_id = ?", Long.class, clienteId);
		if (cuentas.isEmpty()) {
			return false;
		}
		// El mismo token en otra instalación es una reinstalación: la fila vieja ya no
		// sirve.
		jdbc.update("delete from dispositivo_push where md5(token_fcm) = md5(?) and instalacion_id <> ?", tokenFcm,
				instalacionId);
		jdbc.update("""
				insert into dispositivo_push (cuenta_app_id, instalacion_id, token_fcm, plataforma, idioma)
				values (?, ?, ?, ?, ?)
				on conflict (instalacion_id) do update set cuenta_app_id = excluded.cuenta_app_id,
				  token_fcm = excluded.token_fcm, plataforma = excluded.plataforma, idioma = excluded.idioma,
				  activo = true, actualizado_en = now()
				""", cuentas.get(0), instalacionId, tokenFcm, plataforma, idioma);
		return true;
	}

	@Override
	@Transactional
	public void darDeBaja(Long clienteId, String instalacionId) {
		jdbc.update("""
				delete from dispositivo_push where instalacion_id = ?
				  and cuenta_app_id in (select id from cuenta_app where cliente_id = ?)
				""", instalacionId, clienteId);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public List<DispositivoDeCliente> activosDe(Long clienteId) {
		return jdbc.query("""
				select d.id, d.token_fcm, d.idioma from dispositivo_push d
				join cuenta_app c on c.id = d.cuenta_app_id
				where c.cliente_id = ? and d.activo order by d.id
				""", (fila, numero) -> new DispositivoDeCliente(fila.getLong("id"), fila.getString("token_fcm"),
				fila.getString("idioma")), clienteId);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void desactivar(Long dispositivoId) {
		jdbc.update("update dispositivo_push set activo = false, actualizado_en = now() where id = ?", dispositivoId);
	}
}
