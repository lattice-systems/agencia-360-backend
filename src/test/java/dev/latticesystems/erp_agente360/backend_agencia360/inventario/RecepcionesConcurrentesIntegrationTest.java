package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.application.RecepcionRefaccionService;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
 * Comprueba el bloqueo de fila de la recepción: varias recepciones de la misma
 * refacción al mismo tiempo deben sumarse todas, sin que una pise a otra. A
 * diferencia de las demás pruebas, esta confirma sus transacciones (cada hilo
 * tiene la suya), así que borra sus datos al terminar.
 */
class RecepcionesConcurrentesIntegrationTest extends AbstractIntegrationTest {

	private static final int HILOS = 4;
	private static final int RECEPCIONES = 20;

	@Autowired
	private RecepcionRefaccionService recepcionService;

	@Autowired
	private RefaccionRepository refaccionRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Usuario almacen;
	private Refaccion refaccion;

	@BeforeEach
	void crearDatos() {
		Rol rol = rolRepository.findByNombre("INVENTARIO").orElseThrow();
		almacen = usuarioRepository.save(new Usuario("almacen.concurrencia", "hash-de-prueba", "Almacén", rol));
		refaccion = refaccionRepository.save(Refaccion.registrar("CONC-01", "Pieza de prueba concurrente",
				new BigDecimal("10.00"), new BigDecimal("15.00"), 0));
	}

	@AfterEach
	void borrarDatos() {
		jdbcTemplate.update("delete from movimiento_inventario where refaccion_id = ?", refaccion.getId());
		jdbcTemplate.update("delete from refaccion where id = ?", refaccion.getId());
		jdbcTemplate.update("delete from usuario where id = ?", almacen.getId());
	}

	@Test
	void lasRecepcionesSimultaneasSeSumanTodas() throws Exception {
		UsuarioAutenticado actor = new UsuarioAutenticado(almacen.getId(), almacen.getUsuario(),
				Set.of(Role.INVENTARIO));
		CountDownLatch salida = new CountDownLatch(1);
		List<Callable<Void>> tareas = new ArrayList<>();
		for (int i = 0; i < RECEPCIONES; i++) {
			tareas.add(() -> {
				salida.await();
				recepcionService.recibir(refaccion.getId(), 1, actor);
				return null;
			});
		}

		ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
		try {
			List<Future<Void>> resultados = new ArrayList<>();
			for (Callable<Void> tarea : tareas) {
				resultados.add(hilos.submit(tarea));
			}
			salida.countDown();
			for (Future<Void> resultado : resultados) {
				resultado.get(30, TimeUnit.SECONDS);
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(refaccionRepository.findById(refaccion.getId()).orElseThrow().getExistencia())
				.isEqualTo(RECEPCIONES);
		assertThat(jdbcTemplate.queryForObject("select count(*) from movimiento_inventario where refaccion_id = ?",
				Integer.class, refaccion.getId())).isEqualTo(RECEPCIONES);
	}
}
