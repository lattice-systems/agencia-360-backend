package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.FacturaEmitida;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ERP-1.2 de punta a punta: facturacion publica FacturaEmitida al emitir y
 * ventascrm pasa el prospecto de Facturación a Entrega. El evento se atiende
 * después del commit y en otro hilo, así que la prueba confirma sus datos,
 * espera el resultado y borra todo al terminar.
 */
class EntregaAlFacturarIntegrationTest extends AbstractIntegrationTest {

	private static final Duration ESPERA = Duration.ofSeconds(10);

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	private Usuario vendedor;
	private Usuario facturacion;
	private final List<Long> prospectos = new ArrayList<>();

	@BeforeEach
	void setUp() {
		vendedor = usuarioRepository.save(new Usuario("vendedor.entrega", "hash-de-prueba", "Persona vendedor.entrega",
				rolRepository.findByNombre("VENTAS").orElseThrow()));
		facturacion = usuarioRepository.save(new Usuario("facturacion.entrega", "hash-de-prueba",
				"Persona facturacion.entrega", rolRepository.findByNombre("FACTURACION").orElseThrow()));
	}

	@AfterEach
	void limpiar() {
		for (Long id : prospectos) {
			jdbcTemplate.update("delete from historial_etapa where prospecto_id = ?", id);
			jdbcTemplate.update("delete from prospecto where id = ?", id);
		}
		jdbcTemplate.update("delete from usuario where id in (?, ?)", vendedor.getId(), facturacion.getId());
	}

	@Test
	void alEmitirLaFacturaElProspectoPasaAEntregaDesdeElErp() throws InterruptedException {
		long prospecto = prospectoEn("FACTURACION");
		Instant emitida = Instant.now().truncatedTo(ChronoUnit.MICROS);

		emitir(new FacturaEmitida(prospecto, null, "A-0001", emitida, facturacion.getId()));

		esperarEtapa(prospecto, "ENTREGA");
		Map<String, Object> movimiento = jdbcTemplate.queryForMap("select origen, usuario_id, fecha_hora "
				+ "from historial_etapa where prospecto_id = ? order by id desc limit 1", prospecto);
		assertThat(movimiento.get("origen")).isEqualTo("ERP");
		assertThat(((Number) movimiento.get("usuario_id")).longValue()).as("quien emitió la factura")
				.isEqualTo(facturacion.getId());
		assertThat(((Timestamp) movimiento.get("fecha_hora")).toInstant()).isEqualTo(emitida);
	}

	@Test
	void siElEventoLlegaDosVecesSoloQuedaUnMovimientoAEntrega() throws InterruptedException {
		long prospecto = prospectoEn("FACTURACION");
		FacturaEmitida factura = new FacturaEmitida(prospecto, null, "A-0002", Instant.now(), facturacion.getId());

		emitir(factura);
		esperarEtapa(prospecto, "ENTREGA");
		emitir(factura);
		Thread.sleep(500);

		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from historial_etapa where prospecto_id = ? and etapa = 'ENTREGA'", Integer.class,
				prospecto)).isEqualTo(1);
	}

	private void emitir(FacturaEmitida factura) {
		transaccion.executeWithoutResult(estado -> eventos.publishEvent(factura));
	}

	/**
	 * Un prospecto del vendedor en la etapa pedida, sin pasar por las pantallas.
	 */
	private long prospectoEn(String etapa) {
		Long id = jdbcTemplate.queryForObject("""
				insert into prospecto (nombre, telefono, etapa, vendedor_id, etapa_actualizada_en, cierre_pendiente,
				    created_at, updated_at)
				values ('Luis Herrera', '4775550104', ?, ?, now(), false, now(), now())
				returning id
				""", Long.class, etapa, vendedor.getId());
		prospectos.add(id);
		return id;
	}

	private void esperarEtapa(long prospecto, String etapa) throws InterruptedException {
		Instant limite = Instant.now().plus(ESPERA);
		while (Instant.now().isBefore(limite)) {
			String actual = jdbcTemplate.queryForObject("select etapa from prospecto where id = ?", String.class,
					prospecto);
			if (etapa.equals(actual)) {
				return;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("El prospecto " + prospecto + " no pasó a " + etapa + " en " + ESPERA);
	}
}
