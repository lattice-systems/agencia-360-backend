package dev.latticesystems.erp_agente360.backend_agencia360.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.facturacion.application.EmisionFacturaService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.DatosFiscales;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Comprueba los dos bloqueos de fila de la emisión (ERP-1.3): una unidad no se
 * vende con dos facturas, y a un prospecto no se le emiten dos facturas, aunque
 * lleguen al mismo tiempo. Las emisiones confirman sus transacciones, así que
 * la prueba borra sus datos al terminar.
 */
class FacturasConcurrentesIntegrationTest extends AbstractIntegrationTest {

	private static final int INTENTOS = 8;

	@MockitoBean
	private ConsultaFacturacion consultaFacturacion;

	@Autowired
	private EmisionFacturaService emisionFacturaService;

	@Autowired
	private VehiculoRepository vehiculoRepository;

	@Autowired
	private ProspectoRepository prospectoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Usuario vendedor;
	private Usuario facturacion;
	private UsuarioAutenticado actor;
	private final List<Long> prospectos = new ArrayList<>();
	private final List<Long> unidades = new ArrayList<>();

	@BeforeEach
	void crearDatos() {
		vendedor = usuarioRepository.save(new Usuario("vendedor.facturas.concurrencia", "hash-de-prueba", "Vendedor",
				rolRepository.findByNombre("VENTAS").orElseThrow()));
		facturacion = usuarioRepository.save(new Usuario("facturacion.facturas.concurrencia", "hash-de-prueba",
				"Facturación", rolRepository.findByNombre("FACTURACION").orElseThrow()));
		actor = new UsuarioAutenticado(facturacion.getId(), facturacion.getUsuario(), Set.of(Role.FACTURACION));
		for (int i = 0; i < INTENTOS; i++) {
			prospectos.add(prospectoRepository.save(Prospecto.registrar("Cliente concurrente " + i,
					"47755598%02d".formatted(i), null, vendedor.getId(), Instant.now())).getId());
			unidades.add(vehiculoRepository.save(Vehiculo.registrar("3N1AB8CV5PY1988%02d".formatted(i), "Sentra",
					"Advance", 2026, "Blanco", new BigDecimal("489900.00"), null)).getId());
		}
		DatosFiscales receptor = new DatosFiscales("XAXX010101000", "Cliente concurrente", "Calle 1, León, Gto.",
				"616 - Sin obligaciones fiscales");
		when(consultaFacturacion.paraFacturar(anyLong())).thenAnswer(llamada -> Optional
				.of(new ProspectoParaFacturar(llamada.getArgument(0), "Cliente concurrente", vendedor.getId(),
						"Vendedor", new BigDecimal("489900.00"), BigDecimal.ZERO, Instant.now(), receptor)));
	}

	@AfterEach
	void borrarDatos() {
		for (Long id : prospectos) {
			jdbcTemplate.update("delete from factura where prospecto_id = ?", id);
			jdbcTemplate.update("delete from venta where prospecto_id = ?", id);
		}
		for (Long id : unidades) {
			jdbcTemplate.update("delete from vehiculo where id = ?", id);
		}
		for (Long id : prospectos) {
			jdbcTemplate.update("delete from historial_etapa where prospecto_id = ?", id);
			jdbcTemplate.update("delete from prospecto where id = ?", id);
		}
		jdbcTemplate.update("delete from usuario where id in (?, ?)", vendedor.getId(), facturacion.getId());
	}

	@Test
	void unaUnidadSoloSeVendeConUnaFacturaAunqueLaEmitanAlMismoTiempo() throws Exception {
		Long unidad = unidades.get(0);
		List<Callable<Object>> emisiones = new ArrayList<>();
		for (Long prospectoId : prospectos) {
			emisiones.add(() -> emisionFacturaService.emitir(prospectoId, unidad, actor));
		}

		assertThat(emitidas(emisiones)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from factura where vehiculo_id = ?", Integer.class,
				unidad)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, unidad))
				.isEqualTo("VENDIDO");
	}

	@Test
	void unProspectoSoloRecibeUnaFacturaAunqueLaEmitanAlMismoTiempo() throws Exception {
		Long prospecto = prospectos.get(0);
		List<Callable<Object>> emisiones = new ArrayList<>();
		for (Long unidadId : unidades) {
			emisiones.add(() -> emisionFacturaService.emitir(prospecto, unidadId, actor));
		}

		assertThat(emitidas(emisiones)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from factura where prospecto_id = ?", Integer.class,
				prospecto)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from vehiculo where id in (" + ids() + ") and " + "estatus = 'VENDIDO'",
				Integer.class)).as("las demás unidades siguen Disponibles").isEqualTo(1);
	}

	/**
	 * Lanza las emisiones a la vez.
	 *
	 * @return cuántas se emitieron; las demás deben rechazarse con 409
	 */
	private static int emitidas(List<Callable<Object>> emisiones) throws Exception {
		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(4);
		int emitidas = 0;
		try {
			List<Future<Object>> resultados = new ArrayList<>();
			for (Callable<Object> emision : emisiones) {
				resultados.add(hilos.submit(() -> {
					salida.await();
					return emision.call();
				}));
			}
			salida.countDown();
			for (Future<Object> resultado : resultados) {
				try {
					resultado.get(30, TimeUnit.SECONDS);
					emitidas++;
				} catch (ExecutionException e) {
					assertThat(e.getCause()).isInstanceOf(ConflictoException.class);
				}
			}
		} finally {
			hilos.shutdownNow();
		}
		return emitidas;
	}

	private String ids() {
		return String.join(",", unidades.stream().map(String::valueOf).toList());
	}
}
