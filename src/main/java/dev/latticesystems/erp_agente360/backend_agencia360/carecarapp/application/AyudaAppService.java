package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CuentaApp;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.CuentaAppRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas del Centro de ayuda y reportes con datos técnicos (V2-M27). */
@Service
public class AyudaAppService {

	private final JdbcTemplate jdbc;
	private final CuentaAppRepository cuentas;

	public AyudaAppService(JdbcTemplate jdbc, CuentaAppRepository cuentas) {
		this.jdbc = jdbc;
		this.cuentas = cuentas;
	}

	@Transactional(readOnly = true)
	public List<PreguntaFrecuenteApp> preguntasFrecuentes() {
		return jdbc.query(
				"select id, tema, pregunta, respuesta, orden from pregunta_frecuente where activa "
						+ "order by tema, orden, id",
				(rs, fila) -> new PreguntaFrecuenteApp(rs.getLong("id"), rs.getString("tema"), rs.getString("pregunta"),
						rs.getString("respuesta"), rs.getInt("orden")));
	}

	@Transactional
	public void reportar(Long clienteId, String descripcion, String version, String sistema, String dispositivo) {
		if (descripcion.strip().length() < 10) {
			throw new IllegalArgumentException("La descripción debe tener al menos 10 caracteres");
		}
		CuentaApp cuenta = cuentas.findByClienteId(clienteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta CareCar"));
		jdbc.update(
				"insert into reporte_app (cuenta_app_id, descripcion, version_app, sistema, dispositivo) "
						+ "values (?, ?, ?, ?, ?)",
				cuenta.getId(), descripcion.strip(), version.strip(), sistema.strip(), dispositivo.strip());
	}
}
