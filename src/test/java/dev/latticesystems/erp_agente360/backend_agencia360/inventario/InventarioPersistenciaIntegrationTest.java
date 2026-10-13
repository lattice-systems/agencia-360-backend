package dev.latticesystems.erp_agente360.backend_agencia360.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.EstatusVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.MovimientoInventario;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Recepcion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Refaccion;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.TipoMovimiento;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.domain.Vehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.MovimientoInventarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.RefaccionRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.infrastructure.VehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba la migración V4 contra PostgreSQL real (Testcontainers): que el mapeo
 * de las entidades coincide con las tablas y que la base rechaza por su cuenta
 * lo que el dominio no permite. Cada prueba se revierte al terminar.
 */
@Transactional
class InventarioPersistenciaIntegrationTest extends AbstractIntegrationTest {

	private static final Instant AHORA = Instant.parse("2026-10-13T16:00:00Z");
	private static final Instant AYER = Instant.parse("2026-10-12T15:30:00Z");

	@Autowired
	private VehiculoRepository vehiculoRepository;

	@Autowired
	private RefaccionRepository refaccionRepository;

	@Autowired
	private MovimientoInventarioRepository movimientoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void guardaUnVehiculoConSuMovimientoDeEntrada() {
		Usuario responsable = responsable();
		Vehiculo vehiculo = vehiculoRepository.save(vehiculo("3VWFE21C04M000001"));
		movimientoRepository.save(MovimientoInventario.entradaDeVehiculo(vehiculo, responsable.getId(), AHORA));
		limpiarContexto();

		Vehiculo guardado = vehiculoRepository.findById(vehiculo.getId()).orElseThrow();
		assertThat(guardado.getVin()).isEqualTo("3VWFE21C04M000001");
		assertThat(guardado.getPrecio()).isEqualByComparingTo("389900.00");
		assertThat(guardado.getEstatus()).isEqualTo(EstatusVehiculo.DISPONIBLE);
		assertThat(guardado.getCreatedAt()).isNotNull();
		assertThat(movimientoRepository.findAll()).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
			assertThat(movimiento.getVehiculo().getId()).isEqualTo(vehiculo.getId());
			assertThat(movimiento.getCantidad()).isEqualTo(1);
			assertThat(movimiento.getResponsableId()).isEqualTo(responsable.getId());
			assertThat(movimiento.getFechaRegistro()).isEqualTo(AHORA);
			assertThat(movimiento.getFechaLlegada()).isEqualTo(AHORA);
		});
	}

	@Test
	void guardaLaExistenciaDeUnaRefaccionYSuMovimiento() {
		Usuario responsable = responsable();
		Refaccion refaccion = refaccionRepository.save(refaccion("FIL-ACE-01"));
		movimientoRepository.save(
				refaccion.recibir(new Recepcion(12, "Refacciones del Bajío", null, AYER), responsable.getId(), AHORA));
		limpiarContexto();

		Refaccion guardada = refaccionRepository.findById(refaccion.getId()).orElseThrow();
		assertThat(guardada.getExistencia()).isEqualTo(12);
		assertThat(guardada.getCosto()).isEqualByComparingTo("180.50");
		assertThat(movimientoRepository.findAll()).singleElement().satisfies(movimiento -> {
			assertThat(movimiento.getRefaccion().getId()).isEqualTo(refaccion.getId());
			assertThat(movimiento.getProveedor()).isEqualTo("Refacciones del Bajío");
			assertThat(movimiento.getFechaLlegada()).isEqualTo(AYER);
			assertThat(movimiento.getFechaRegistro()).isEqualTo(AHORA);
		});
	}

	@Test
	void noPermiteDosVehiculosConElMismoVin() {
		vehiculoRepository.saveAndFlush(vehiculo("3VWFE21C04M000001"));

		assertThatThrownBy(() -> vehiculoRepository.saveAndFlush(vehiculo("3VWFE21C04M000001")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void noPermiteDosRefaccionesConElMismoCodigo() {
		refaccionRepository.saveAndFlush(refaccion("FIL-ACE-01"));

		assertThatThrownBy(() -> refaccionRepository.saveAndFlush(refaccion("FIL-ACE-01")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseNoPermiteExistenciaNegativa() {
		Refaccion refaccion = refaccionRepository.saveAndFlush(refaccion("FIL-ACE-01"));

		assertThatThrownBy(
				() -> jdbcTemplate.update("update refaccion set existencia = -1 where id = ?", refaccion.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseExigeQueElMovimientoSeaDeUnVehiculoODeUnaRefaccion() {
		Usuario responsable = responsable();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into movimiento_inventario (tipo, cantidad, responsable_id, fecha_llegada, fecha_registro) "
						+ "values ('ENTRADA', 1, ?, now(), now())",
				responsable.getId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseNoPermiteMasDeUnaUnidadPorMovimientoDeVehiculo() {
		Usuario responsable = responsable();
		Vehiculo vehiculo = vehiculoRepository.saveAndFlush(vehiculo("3VWFE21C04M000001"));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into movimiento_inventario (tipo, vehiculo_id, cantidad, responsable_id, fecha_llegada, "
						+ "fecha_registro) values ('ENTRADA', ?, 2, ?, now(), now())",
				vehiculo.getId(), responsable.getId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseNoPermiteUnaLlegadaPosteriorAlRegistro() {
		Usuario responsable = responsable();
		Refaccion refaccion = refaccionRepository.saveAndFlush(refaccion("FIL-ACE-01"));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into movimiento_inventario (tipo, refaccion_id, cantidad, responsable_id, proveedor, "
						+ "fecha_llegada, fecha_registro) "
						+ "values ('ENTRADA', ?, 1, ?, 'Bajío', now() + interval '1 day', now())",
				refaccion.getId(), responsable.getId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laBaseExigeProveedorEnLasEntradasDeRefacciones() {
		Usuario responsable = responsable();
		Refaccion refaccion = refaccionRepository.saveAndFlush(refaccion("FIL-ACE-01"));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into movimiento_inventario (tipo, refaccion_id, cantidad, responsable_id, fecha_llegada, "
						+ "fecha_registro) values ('ENTRADA', ?, 1, ?, now(), now())",
				refaccion.getId(), responsable.getId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	private void limpiarContexto() {
		entityManager.flush();
		entityManager.clear();
	}

	private Usuario responsable() {
		Rol rol = rolRepository.findByNombre("INVENTARIO").orElseThrow();
		return usuarioRepository.saveAndFlush(new Usuario("almacen1", "hash-de-prueba", "Persona de almacén", rol));
	}

	private static Vehiculo vehiculo(String vin) {
		return Vehiculo.registrar(vin, "Jetta", "Comfortline", 2026, "Blanco", new BigDecimal("389900.00"));
	}

	private static Refaccion refaccion(String codigo) {
		return Refaccion.registrar(codigo, "Filtro de aceite", new BigDecimal("180.50"), new BigDecimal("320.00"), 5);
	}
}
