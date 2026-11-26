package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ExportadorDeDatos;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La copia de los datos de una cuenta (acceso ARCO, V2-M22.02) con una sección
 * por tabla. Cada una es una consulta que PostgreSQL convierte a JSON con
 * {@code to_jsonb}: una columna nueva entra sola al archivo, sin que nadie se
 * acuerde de agregarla.
 *
 * <p>
 * Corre en su propia transacción: si una consulta falla, no estropea la
 * transacción que marca la solicitud como fallida.
 */
@Repository
public class ExportadorDeDatosJdbc implements ExportadorDeDatos {

	/**
	 * Una sección del archivo. Las consultas devuelven siempre {@code id} (el
	 * orden) y reciben {@code :c}, el id del cliente. {@code unica} la entrega como
	 * objeto y no como lista.
	 */
	private record Seccion(String nombre, String sql, boolean unica) {

		static Seccion lista(String nombre, String sql) {
			return new Seccion(nombre, sql, false);
		}

		static Seccion unica(String nombre, String sql) {
			return new Seccion(nombre, sql, true);
		}
	}

	private static final List<Seccion> SECCIONES = List.of(
			Seccion.unica("cliente", "select id, nombre, correo, telefono, created_at from cliente where id = :c"),
			// Sin el hash de la contraseña ni los intentos fallidos: no son datos de la
			// persona sino de la seguridad de la cuenta.
			Seccion.unica("cuentaApp", """
					select id, usuario, activa, debe_cambiar_contrasena, created_at
					from cuenta_app where cliente_id = :c
					"""), Seccion.lista("vehiculos", "select * from vehiculo_cliente where cliente_id = :c"),
			Seccion.lista("citas", "select * from cita where cliente_id = :c"), Seccion.lista("traslados", """
					select t.* from traslado t join cita c on c.id = t.cita_id where c.cliente_id = :c
					"""), Seccion.lista("checklistsDeTraslado", """
					select h.* from checklist_traslado h
					join traslado t on t.id = h.traslado_id join cita c on c.id = t.cita_id
					where c.cliente_id = :c
					"""), Seccion.lista("direcciones", "select * from direccion_cliente where cliente_id = :c"),
			Seccion.lista("perfilesFiscales", "select * from perfil_fiscal where cliente_id = :c"),
			Seccion.lista("pagos", """
					select id, orden_servicio_id, monto, moneda, procesador, intencion_id, metodo, marca, ultimos4,
					  estado, motivo_fallo, pagado_en, creado_en
					from pago_app where cliente_id = :c
					"""), Seccion.lista("tarjetasGuardadas", """
					select id, marca, ultimos4, mes_vence, anio_vence, tipo, predeterminado, creado_en
					from metodo_pago where cliente_id = :c
					"""),
			Seccion.lista("servicios", """
					select o.* from orden_servicio o
					join vehiculo_cliente v on v.id = o.vehiculo_cliente_id where v.cliente_id = :c
					"""),
			Seccion.lista("estimacionesTomaCuenta", "select * from estimacion_toma_cuenta where cliente_id = :c"),
			// Sin las claves de las fotos ni quién del personal capturó la oferta.
			Seccion.lista("valuaciones", """
					select id, estimacion_toma_cuenta_id, vehiculo_cliente_id, cita_id, estado, oferta, oferta_nota,
					  vigencia_hasta, ofertada_en, respondida_en, created_at, updated_at
					from valuacion where cliente_id = :c
					"""), Seccion.lista("solicitudesEstreno", "select * from solicitud_estreno where cliente_id = :c"),
			Seccion.lista("solicitudesAsistencia", "select * from solicitud_asistencia where cliente_id = :c"),
			Seccion.lista("consultasAsistente", "select * from consulta_asistente where cliente_id = :c"),
			Seccion.lista("lecturasKm", """
					select l.* from lectura_km l
					join vehiculo_cliente v on v.id = l.vehiculo_cliente_id where v.cliente_id = :c
					"""), Seccion.lista("gastos", """
					select g.* from gasto_vehiculo g
					join vehiculo_cliente v on v.id = g.vehiculo_cliente_id where v.cliente_id = :c
					"""), Seccion.lista("avisosAceptados", """
					select a.id, v.version, v.vigente_desde, a.aceptado_en
					from aceptacion_aviso a
					join cuenta_app c on c.id = a.cuenta_app_id
					join aviso_privacidad v on v.id = a.aviso_privacidad_id
					where c.cliente_id = :c
					"""), Seccion.lista("solicitudesArco",
					"select id, tipo, estado, creada_en, resuelta_en from solicitud_arco where cliente_id = :c"));

	private static final List<String> INCLUYE = List.of("Tus datos de contacto", "Tu cuenta de la app (sin contraseña)",
			"Tus vehículos, citas, traslados y direcciones", "Tus datos fiscales para facturar",
			"Tus pagos desde la app y tus tarjetas guardadas (sin el número completo)",
			"El historial de servicios de tus vehículos",
			"Tus estimaciones de toma a cuenta, tus valuaciones formales y tus solicitudes de prueba de manejo o "
					+ "cotización",
			"Tus solicitudes de asistencia y tus preguntas al asistente",
			"Tu bitácora: lecturas de kilometraje y gastos", "Los avisos de privacidad que aceptaste",
			"Tus solicitudes de derechos ARCO");

	private static final List<String> NO_INCLUYE = List.of(
			"Los datos fiscales y comerciales que la agencia conserva por obligación legal (ventas, pagos y facturas): "
					+ "pídelos en la agencia",
			"Las notas internas del personal de la agencia", "Las fotos y los archivos adjuntos");

	private final NamedParameterJdbcTemplate jdbc;
	private final ObjectMapper json;

	public ExportadorDeDatosJdbc(JdbcTemplate jdbc, ObjectMapper json) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.json = json;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public byte[] exportar(long clienteId, Instant ahora) {
		ObjectNode raiz = json.createObjectNode();
		raiz.put("generadoEn", ahora.toString());
		raiz.put("descripcion", "Copia de los datos personales que CareCar guarda de tu cuenta (derecho de acceso). "
				+ "Contiene información personal: guárdala en un lugar seguro y no la compartas.");
		ObjectNode alcance = raiz.putObject("alcance");
		INCLUYE.forEach(alcance.putArray("incluye")::add);
		NO_INCLUYE.forEach(alcance.putArray("noIncluye")::add);
		for (Seccion seccion : SECCIONES) {
			JsonNode datos = leer(seccion, clienteId);
			raiz.set(seccion.nombre(), seccion.unica() ? (datos.isEmpty() ? json.nullNode() : datos.get(0)) : datos);
		}
		try {
			return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(raiz);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("No se pudo escribir el archivo de datos", ex);
		}
	}

	private JsonNode leer(Seccion seccion, long clienteId) {
		String texto = jdbc
				.queryForObject("select coalesce(jsonb_agg(to_jsonb(t) order by t.id), '[]'::jsonb)::text from ("
						+ seccion.sql() + ") t", Map.of("c", clienteId), String.class);
		try {
			return json.readTree(texto);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("La sección " + seccion.nombre() + " no es JSON", ex);
		}
	}
}
