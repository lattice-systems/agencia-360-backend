package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AnonimizadorDeCuenta;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Qué se borra, qué se anonimiza y qué se conserva de una cuenta que su titular
 * canceló (V2-M22.03). Cada consulta va acotada al cliente y dice por qué.
 *
 * <p>
 * Los archivos que subió la persona (fotos del tablero y de sus valuaciones,
 * tickets) se borran del almacén solo cuando la transacción se confirma: si
 * algo falla y todo revierte, las filas siguen ahí y no pueden quedarse
 * apuntando a un archivo que ya no está.
 */
@Repository
public class AnonimizadorDeCuentaJdbc implements AnonimizadorDeCuenta {

	private static final String DE_SUS_VEHICULOS = "(select id from vehiculo_cliente where cliente_id = ?)";
	private static final String DE_SU_CUENTA = "(select id from cuenta_app where cliente_id = ?)";

	private final JdbcTemplate jdbc;
	private final AlmacenArchivos archivos;

	public AnonimizadorDeCuentaJdbc(JdbcTemplate jdbc, AlmacenArchivos archivos) {
		this.jdbc = jdbc;
		this.archivos = archivos;
	}

	@Override
	public void anonimizar(long clienteId) {
		// Copias de datos que la persona pidió antes: no se guardan de más. La
		// solicitud queda, porque prueba qué se pidió.
		jdbc.update("delete from archivo_arco where solicitud_arco_id in "
				+ "(select id from solicitud_arco where cliente_id = ?)", clienteId);
		jdbc.update("update solicitud_arco set archivo_url = null where cliente_id = ? and archivo_url is not null",
				clienteId);

		// Bitácora personal. Los archivos se borran al confirmar. Las lecturas del
		// taller son historial de servicio y se conservan.
		List<String> archivosPropios = jdbc.queryForList("""
				select l.foto_url from lectura_km l
				where l.fuente = 'CLIENTE' and l.foto_url is not null and l.vehiculo_cliente_id in
				""" + DE_SUS_VEHICULOS + """
				union all
				select g.ticket_url from gasto_vehiculo g
				where g.ticket_url is not null and g.vehiculo_cliente_id in
				""" + DE_SUS_VEHICULOS, String.class, clienteId, clienteId);
		List<String> aBorrar = new ArrayList<>(archivosPropios);
		jdbc.update("delete from gasto_vehiculo where vehiculo_cliente_id in " + DE_SUS_VEHICULOS, clienteId);
		jdbc.update("delete from lectura_km where fuente = 'CLIENTE' and vehiculo_cliente_id in " + DE_SUS_VEHICULOS,
				clienteId);

		// Preguntas y respuestas libres al asistente.
		jdbc.update("delete from consulta_asistente where cliente_id = ?", clienteId);

		// Intereses comerciales: no son obligatorios por ley. El prospecto del CRM
		// no se toca (es de otro módulo y puede ser fiscal).
		jdbc.update("update solicitud_estreno set estimacion_toma_cuenta_id = null where cliente_id = ?", clienteId);
		jdbc.update("delete from solicitud_estreno where cliente_id = ?", clienteId);
		// Las valuaciones van antes que las estimaciones: las referencian. Sus fotos
		// muestran placas y a veces personas, así que también se borran. La cita de
		// inspección se conserva como las demás y el prospecto no se toca.
		aBorrar.addAll(jdbc.queryForList("""
				select foto.value ->> 'clave' from valuacion v, jsonb_each(v.fotos::jsonb) foto
				where v.cliente_id = ?
				""", String.class, clienteId));
		jdbc.update("delete from valuacion where cliente_id = ?", clienteId);
		jdbc.update("delete from estimacion_toma_cuenta where cliente_id = ?", clienteId);

		// Asistencia en carretera: queda el registro del servicio, sin dónde estaba
		// la persona ni lo que contó.
		jdbc.update("update solicitud_asistencia set lat = null, lng = null, comentario = null where cliente_id = ?",
				clienteId);

		// Direcciones: los traslados las referencian y no se pueden borrar. Se
		// vacían; el pin en 0,0 es el único que cumple la restricción.
		jdbc.update("""
				update direccion_cliente set alias = 'Eliminada', calle = 'Dirección eliminada',
				  colonia = 'Eliminada', cp = '00000', ciudad = 'Eliminada', lat = 0, lng = 0, updated_at = now()
				where cliente_id = ?
				""", clienteId);

		// Datos fiscales (V2-M08.05): son de la persona y no se conservan. Las
		// facturas guardan su propia copia del receptor, que sí se conserva por ley.
		jdbc.update("delete from perfil_fiscal where cliente_id = ?", clienteId);

		// Tarjetas guardadas (V2-M08.04): se borran. Los pagos son dinero y se
		// conservan, sin el token de la tarjeta.
		jdbc.update("update pago_app set token_procesador = null where cliente_id = ?", clienteId);
		jdbc.update("delete from metodo_pago where cliente_id = ?", clienteId);

		// Ya no se entra ni se recupera la cuenta.
		jdbc.update("delete from sesion_app where cuenta_id in " + DE_SU_CUENTA, clienteId);
		jdbc.update("delete from token_recuperacion where cuenta_app_id in " + DE_SU_CUENTA, clienteId);

		borrarAlConfirmar(aBorrar);
	}

	private void borrarAlConfirmar(List<String> claves) {
		if (claves.isEmpty()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			claves.forEach(archivos::borrar);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				claves.forEach(archivos::borrar);
			}
		});
	}
}
