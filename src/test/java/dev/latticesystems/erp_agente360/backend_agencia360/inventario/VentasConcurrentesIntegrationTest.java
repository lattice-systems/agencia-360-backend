package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.AsignacionUnidadService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.ConflictoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

/**
 * Comprueba el bloqueo de fila de la unidad (ERP-1.0): si varias personas de
 * Facturación eligen la misma unidad al mismo tiempo para prospectos distintos,
 * solo una venta se la queda. Confirma sus transacciones, así que borra sus
 * datos al terminar.
 */
class VentasConcurrentesIntegrationTest extends AbstractIntegrationTest {

	private static final int PROSPECTOS = 8;

	@Autowired
	private AsignacionUnidadService asignacionService;

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
	private Vehiculo unidad;
	private final List<Long> prospectos = new ArrayList<>();

	@BeforeEach
	void crearDatos() {
		vendedor = usuarioRepository.save(new Usuario("vendedor.concurrencia", "hash-de-prueba", "Vendedor",
				rolRepository.findByNombre("VENTAS").orElseThrow()));
		facturacion = usuarioRepository.save(new Usuario("facturacion.concurrencia", "hash-de-prueba", "Facturación",
				rolRepository.findByNombre("FACTURACION").orElseThrow()));
		unidad = vehiculoRepository.save(Vehiculo.registrar("3N1AB8CV5PY199999", "Sentra", "Advance", 2026, "Blanco",
				new BigDecimal("489900.00"), null));
		for (int i = 0; i < PROSPECTOS; i++) {
			Prospecto prospecto = prospectoRepository.save(Prospecto.registrar("Cliente concurrente " + i,
					"47755599%02d".formatted(i), null, vendedor.getId(), Instant.now()));
			prospectos.add(prospecto.getId());
		}
		jdbcTemplate.update("update prospecto set etapa = 'FACTURACION' where vendedor_id = ?", vendedor.getId());
	}

	@AfterEach
	void borrarDatos() {
		jdbcTemplate.update("delete from venta where vehiculo_id = ?", unidad.getId());
		jdbcTemplate.update("delete from vehiculo where id = ?", unidad.getId());
		for (Long id : prospectos) {
			jdbcTemplate.update("delete from historial_etapa where prospecto_id = ?", id);
			jdbcTemplate.update("delete from prospecto where id = ?", id);
		}
		jdbcTemplate.update("delete from usuario where id in (?, ?)", vendedor.getId(), facturacion.getId());
	}

	@Test
	void soloUnaVentaSeQuedaConLaUnidadAunqueLaElijanAlMismoTiempo() throws Exception {
		UsuarioAutenticado actor = new UsuarioAutenticado(facturacion.getId(), facturacion.getUsuario(),
				Set.of(Role.FACTURACION));
		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(4);
		int vendidas = 0;
		int rechazadas = 0;
		try {
			List<Future<?>> resultados = new ArrayList<>();
			for (Long prospectoId : prospectos) {
				resultados.add(hilos.submit(() -> {
					salida.await();
					return asignacionService.asignar(prospectoId, unidad.getId(), actor);
				}));
			}
			salida.countDown();
			for (Future<?> resultado : resultados) {
				try {
					resultado.get(30, TimeUnit.SECONDS);
					vendidas++;
				} catch (ExecutionException e) {
					assertThat(e.getCause()).isInstanceOf(ConflictoException.class);
					rechazadas++;
				}
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(vendidas).isEqualTo(1);
		assertThat(rechazadas).isEqualTo(PROSPECTOS - 1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from venta where vehiculo_id = ?", Integer.class,
				unidad.getId())).isEqualTo(1);
		assertThat(
				jdbcTemplate.queryForObject("select estatus from vehiculo where id = ?", String.class, unidad.getId()))
				.isEqualTo("VENDIDO");
	}
}
