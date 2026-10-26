package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.ConsultaFacturacion.ProspectoParaFacturar;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.Prospecto;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * La consulta que ventascrm ofrece a facturacion (ERP-1.3): los prospectos en
 * Facturación con su total, su saldo y el vendedor. Cada prueba se revierte al
 * terminar.
 */
@Transactional
class ConsultaFacturacionIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ConsultaFacturacion consultaFacturacion;

	@Autowired
	private ProspectoRepository prospectoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void regresaSoloLosProspectosEnFacturacionConSuVendedor() {
		Usuario vendedor = registrar("vendedor.facturar", Role.VENTAS);
		long luis = prospecto(vendedor, "Luis Herrera", "4775550104", "FACTURACION");
		prospecto(vendedor, "Ana López", "4775550101", "PAGO");
		prospecto(vendedor, "Valeria Díaz", "4775550105", "ENTREGA");

		List<ProspectoParaFacturar> lista = consultaFacturacion.enFacturacion();

		assertThat(lista).extracting(ProspectoParaFacturar::prospectoId).containsExactly(luis);
		ProspectoParaFacturar deLuis = lista.get(0);
		assertThat(deLuis.nombre()).isEqualTo("Luis Herrera");
		assertThat(deLuis.vendedorId()).isEqualTo(vendedor.getId());
		assertThat(deLuis.vendedor()).isEqualTo("Persona vendedor.facturar");
		assertThat(deLuis.enFacturacionDesde()).isNotNull();
	}

	@Test
	void calculaElSaldoSinLosPagosAnulados() {
		Usuario vendedor = registrar("vendedor.saldo", Role.VENTAS);
		Usuario caja = registrar("caja.saldo", Role.CAJA);
		long luis = prospecto(vendedor, "Luis Herrera", "4775550104", "FACTURACION");
		long ana = prospecto(vendedor, "Ana López", "4775550101", "FACTURACION");
		long sinPrecio = prospecto(vendedor, "Carlos Núñez", "4775550109", "FACTURACION");
		long enPago = prospecto(vendedor, "Valeria Díaz", "4775550105", "PAGO");
		conPagos(luis, "489900.00", caja, "400000.00", "89900.00");
		conPagos(ana, "329900.00", caja, "329900.00", null);

		assertThat(consultaFacturacion.enFacturacion()).extracting(ProspectoParaFacturar::prospectoId)
				.containsExactly(sinPrecio, ana, luis);
		ProspectoParaFacturar deLuis = consultaFacturacion.paraFacturar(luis).orElseThrow();
		assertThat(deLuis.total()).isEqualByComparingTo("489900.00");
		assertThat(deLuis.saldo()).isEqualByComparingTo("89900.00");
		assertThat(deLuis.receptor()).as("los datos fiscales llegan con la V17").isNull();
		assertThat(consultaFacturacion.paraFacturar(ana).orElseThrow().saldo()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(consultaFacturacion.paraFacturar(sinPrecio).orElseThrow().saldo()).isNull();
		assertThat(consultaFacturacion.paraFacturar(enPago)).isEmpty();
		assertThat(consultaFacturacion.paraFacturar(999_999L)).isEmpty();
	}

	/**
	 * Precio total y pagos de la venta; el segundo pago, si lo hay, está anulado y
	 * no cuenta.
	 */
	private void conPagos(long prospecto, String precio, Usuario caja, String pagado, String anulado) {
		jdbcTemplate.update("update prospecto set precio_total = ? where id = ?", new BigDecimal(precio), prospecto);
		Long liquidacion = jdbcTemplate.queryForObject("""
				insert into liquidacion (prospecto_id, estado, created_at, updated_at)
				values (?, 'VALIDADA', now(), now()) returning id""", Long.class, prospecto);
		pago(liquidacion, pagado, caja, false);
		if (anulado != null) {
			pago(liquidacion, anulado, caja, true);
		}
	}

	private void pago(Long liquidacion, String monto, Usuario caja, boolean anulado) {
		jdbcTemplate.update("""
				insert into pago (liquidacion_id, monto, fecha_pago, registrado_por_id, anulado, created_at, updated_at)
				values (?, ?, current_date, ?, ?, now(), now())""", liquidacion, new BigDecimal(monto), caja.getId(),
				anulado);
	}

	private long prospecto(Usuario vendedor, String nombre, String telefono, String etapa) {
		Prospecto prospecto = prospectoRepository
				.saveAndFlush(Prospecto.registrar(nombre, telefono, null, vendedor.getId(), Instant.now()));
		jdbcTemplate.update("update prospecto set etapa = ? where id = ?", etapa, prospecto.getId());
		entityManager.clear();
		return prospecto.getId();
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}
}
