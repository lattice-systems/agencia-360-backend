package dev.latticesystems.erp_agente360.backend_agencia360.ventascrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.inventario.VentaDeUnidades;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.eventos.ProspectoFinalizado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.domain.DatosFiscalesDePrueba;
import dev.latticesystems.erp_agente360.backend_agencia360.ventascrm.infrastructure.ProspectoRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba prospectos de punta a punta (CRM-1.0, CRM-1.1 y la regla de
 * visibilidad de CRM-0.1): migración, seguridad, endpoints y base de datos real
 * (Testcontainers). Cada prueba se revierte al terminar.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class ProspectosIntegrationTest extends AbstractIntegrationTest {

	private static final String URL = "/api/v1/prospectos";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VentaDeUnidades ventaDeUnidades;

	@Autowired
	private ProspectoRepository prospectoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ApplicationEvents eventos;

	@Test
	void unVendedorRegistraUnProspectoEnPrimerContacto() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","telefono":"4771234567"}
				""")).andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.nombre").value("Ana López"))
				.andExpect(jsonPath("$.telefono").value("4771234567")).andExpect(jsonPath("$.correo").isEmpty())
				.andExpect(jsonPath("$.etapa").value("PRIMER_CONTACTO"))
				.andExpect(jsonPath("$.vendedorId").value(vendedor.getId()))
				.andExpect(jsonPath("$.creadoEn").isNotEmpty()).andExpect(jsonPath("$.contactoRepetido").value(false));
	}

	@Test
	void elRegistroGuardaElPrimerMovimientoDelHistorial() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		long id = idDe(mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","correo":"ana@ejemplo.com"}
				""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		List<Map<String, Object>> historial = historialDe(id);
		assertThat(historial).singleElement().satisfies(movimiento -> {
			assertThat(movimiento).containsEntry("etapa", "PRIMER_CONTACTO").containsEntry("origen", "CRM");
			assertThat(movimiento.get("fecha_hora")).isNotNull();
		});
	}

	@Test
	void rechazaUnProspectoSinTelefonoNiCorreo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López"}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Captura al menos un teléfono o un correo"));
	}

	@Test
	void rechazaUnProspectoSinNombre() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":" ","telefono":"4771234567"}
				""")).andExpect(status().isBadRequest());
	}

	@Test
	void rechazaUnCorreoMalEscrito() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","correo":"ana-sin-arroba"}
				""")).andExpect(status().isBadRequest());
	}

	@Test
	void registraUnTelefonoRepetidoDeOtroVendedorYSoloAvisaSinDecirDeQuien() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		crearProspecto(vendedor1, "Ana López", "4771234567", null);

		mockMvc.perform(registro(token(vendedor2), """
				{"nombre":"Ana López","telefono":"4771234567"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.contactoRepetido").value(true))
				.andExpect(jsonPath("$.vendedorId").value(vendedor2.getId()));

		// Cada vendedor conserva su propio prospecto.
		mockMvc.perform(conToken(get(URL), token(vendedor1))).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(conToken(get(URL), token(vendedor2))).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void avisaQueElCorreoYaExisteSinImportarMayusculas() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		crearProspecto(vendedor, "Ana López", null, "ana@ejemplo.com");

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana","correo":"ANA@Ejemplo.com"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.contactoRepetido").value(true));
	}

	@Test
	void rechazaUnTelefonoQueNoEsDeDiezDigitos() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(registro(token(vendedor), """
				{"nombre":"Ana López","telefono":"477123"}
				""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El teléfono debe tener 10 dígitos"));
	}

	@Test
	void soloVentasRegistraProspectos() throws Exception {
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);

		mockMvc.perform(registro(token(gerente), """
				{"nombre":"Ana López","telefono":"4771234567"}
				""")).andExpect(status().isForbidden());
	}

	@Test
	void exigeIniciarSesion() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void unVendedorSoloVeSusProspectos() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		crearProspecto(vendedor1, "Ana López", "4771234567", null);
		crearProspecto(vendedor2, "Luis Pérez", "4779876543", null);

		mockMvc.perform(conToken(get(URL), token(vendedor1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Ana López"));
	}

	@Test
	void elGerenteDeMarketingVeLosProspectosDeTodos() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		crearProspecto(vendedor1, "Ana López", "4771234567", null);
		crearProspecto(vendedor2, "Luis Pérez", "4779876543", null);

		mockMvc.perform(conToken(get(URL), token(gerente))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void contabilidadNoVeProspectosMientrasElPoNoDefinaSuFuncionEnElCrm() throws Exception {
		Usuario contador = registrar("conta1", Role.CONTABILIDAD);

		mockMvc.perform(conToken(get(URL), token(contador))).andExpect(status().isForbidden());
	}

	@Test
	void filtraLaListaPorEtapa() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long ana = crearProspecto(vendedor, "Ana López", "4771234567", null);
		crearProspecto(vendedor, "Luis Pérez", "4779876543", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), ana, "VISITA")).andExpect(status().isOk());

		mockMvc.perform(conToken(get(URL).param("etapa", "VISITA"), token(vendedor))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Ana López"));
	}

	@Test
	void rechazaUnFiltroConUnaEtapaQueNoExiste() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);

		mockMvc.perform(conToken(get(URL).param("etapa", "GANADO"), token(vendedor))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Etapa no válida: GANADO"));
	}

	@Test
	void cambiarLaEtapaRegistraElMovimientoEnElHistorial() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "COTIZACION")).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("COTIZACION"));

		assertThat(historialDe(id)).extracting(movimiento -> movimiento.get("etapa")).containsExactly("PRIMER_CONTACTO",
				"COTIZACION");
	}

	@Test
	void alCerrarseGuardaLaUltimaEtapa() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "COTIZACION")).andExpect(status().isOk());

		mockMvc.perform(cierre(token(vendedor), id, "SE_FUE_POR_OTRA_MARCA")).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("CERRADO"))
				.andExpect(jsonPath("$.motivoCierre").value("SE_FUE_POR_OTRA_MARCA"))
				.andExpect(jsonPath("$.ultimaEtapa").value("COTIZACION"));

		entityManager.flush();
		assertThat(jdbcTemplate.queryForMap("select ultima_etapa, motivo_cierre from prospecto where id = ?", id))
				.containsEntry("ultima_etapa", "COTIZACION").containsEntry("motivo_cierre", "SE_FUE_POR_OTRA_MARCA");
	}

	@Test
	void soloSeCierraConUnoDeLosTresMotivosDelPo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cierre(token(vendedor), id, "Compró en otra agencia")).andExpect(status().isBadRequest());
		mockMvc.perform(cierre(token(vendedor), id, "SIN_CATEGORIA")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Elige uno de los motivos de cierre: Se fue por otra marca, "
						+ "Perdió el interés o Rechazo de financiamiento"));
		assertThat(historialDe(id)).hasSize(1);
	}

	@Test
	void noSePuedeCerrarSinMotivo() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "CERRADO")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El motivo de cierre es obligatorio"));
		assertThat(historialDe(id)).hasSize(1);
	}

	@Test
	void unVendedorNoPuedeCambiarUnProspectoAjenoYRecibe404() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		long id = crearProspecto(vendedor1, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor2), id, "VISITA")).andExpect(status().isNotFound());
		assertThat(historialDe(id)).hasSize(1);
	}

	@Test
	void rechazaUnaEtapaQueNoExisteAlCambiar() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "GANADO")).andExpect(status().isBadRequest());
	}

	@Test
	void elGerenteDeMarketingNoCambiaEtapas() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(gerente), id, "VISITA")).andExpect(status().isForbidden());
	}

	@Test
	void elVendedorSeSaltaEtapasHastaPagoPeroAhiYaNoMueveNada() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "PAGO")).andExpect(status().isOk());

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "FACTURACION")).andExpect(status().isConflict()).andExpect(
				jsonPath("$.message").value("En Pago el prospecto avanza cuando Contabilidad valida el pago"));
		mockMvc.perform(cierre(token(vendedor), id, "PERDIO_EL_INTERES")).andExpect(status().isConflict());
	}

	@Test
	void contabilidadValidaLosPagosFacturacionFacturaYElVendedorFinaliza() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", "ana@ejemplo.com");

		llevarAEntrega(vendedor, id);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "FINALIZADO")).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("FINALIZADO"));

		assertThat(historialDe(id)).extracting(movimiento -> movimiento.get("origen")).containsExactly("CRM", "CRM",
				"ERP", "ERP", "CRM");
	}

	@Test
	void alPasarAPagoSePideElPrecioTotal() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapaSinPrecio(token(vendedor), id, "PAGO")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Captura el precio total de la venta"));
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "PAGO")).andExpect(status().isOk())
				.andExpect(jsonPath("$.precioTotal").value(489900));
	}

	@Test
	void alPasarAPagoSePidenLosDatosFiscalesDelCliente() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(conToken(patch(URL + "/{id}/etapa", id), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON).content("{\"etapa\":\"PAGO\",\"precioTotal\":489900}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Captura los datos fiscales del cliente para la factura"));
		mockMvc.perform(conToken(patch(URL + "/{id}/etapa", id), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"PAGO\",\"precioTotal\":489900,\"datosFiscales\":{\"rfc\":\"LOHA851301AB1\","
						+ "\"nombre\":\"Ana\",\"domicilio\":" + DatosFiscalesDePrueba.DOMICILIO_JSON
						+ ",\"regimenFiscal\":\"612\"}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El RFC no tiene un formato válido"));
		mockMvc.perform(conToken(patch(URL + "/{id}/etapa", id), token(vendedor))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"PAGO\",\"precioTotal\":489900,\"datosFiscales\":{\"rfc\":\"LOHA850101AB1\","
						+ "\"nombre\":\"Ana\",\"domicilio\":" + DatosFiscalesDePrueba.DOMICILIO_JSON
						+ ",\"regimenFiscal\":\"999\"}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El régimen fiscal 999 no está en el catálogo del SAT"));

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "PAGO")).andExpect(status().isOk())
				.andExpect(jsonPath("$.datosFiscales.rfc").value("LOHA850101AB1"))
				.andExpect(jsonPath("$.datosFiscales.nombre").value("Ana López Hernández"))
				.andExpect(jsonPath("$.datosFiscales.regimenFiscal").value("612"))
				.andExpect(jsonPath("$.datosFiscales.regimenFiscalDescripcion")
						.value("Personas Físicas con Actividades Empresariales y Profesionales"));
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("select rfc from prospecto where id = ?", String.class, id))
				.isEqualTo("LOHA850101AB1");
	}

	@Test
	void elVendedorCorrigeLosDatosFiscalesSoloMientrasElProspectoEstaEnPago() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long enPago = crearProspecto(vendedor, "Ana López", "4771234567", null);
		long enVisita = crearProspecto(vendedor, "Luis Pérez", "4771234568", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), enPago, "PAGO")).andExpect(status().isOk());
		String corregidos = "{\"rfc\":\"abc010203xy9\",\"nombre\":\"Autopartes del Bajío SA de CV\"," + "\"domicilio\":"
				+ DatosFiscalesDePrueba.DOMICILIO_JSON + ",\"regimenFiscal\":\"601\"}";

		mockMvc.perform(datosFiscales(token(vendedor), enPago, corregidos)).andExpect(status().isOk())
				.andExpect(jsonPath("$.datosFiscales.rfc").value("ABC010203XY9"))
				.andExpect(jsonPath("$.datosFiscales.regimenFiscal").value("601"));
		mockMvc.perform(datosFiscales(token(vendedor), enVisita, corregidos)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Los datos fiscales se capturan mientras el prospecto está en Pago"));
		mockMvc.perform(datosFiscales(token(registrar("vendedor2", Role.VENTAS)), enPago, corregidos))
				.andExpect(status().isNotFound());
		mockMvc.perform(datosFiscales(token(registrar("gerente1", Role.GERENTE_MARKETING)), enPago, corregidos))
				.andExpect(status().isForbidden());
		mockMvc.perform(datosFiscales(token(vendedor), enPago, "{\"rfc\":\"ABC010203XY9\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void facturacionCorrigeLosDatosFiscalesSoloMientrasElProspectoEstaEnFacturacion() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		String facturista = token(registrar("facturacion1", Role.FACTURACION));
		long enFacturacion = crearProspecto(vendedor, "Ana López", "4771234567", null);
		long enPago = crearProspecto(vendedor, "Luis Pérez", "4771234568", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), enFacturacion, "PAGO")).andExpect(status().isOk());
		mockMvc.perform(cambioDeEtapa(token(vendedor), enPago, "PAGO")).andExpect(status().isOk());
		entityManager.flush();
		jdbcTemplate.update("update prospecto set etapa = 'FACTURACION' where id = ?", enFacturacion);
		entityManager.clear();
		String corregidos = "{\"rfc\":\"LOHA850101AB2\",\"nombre\":\"Ana López Hernández\",\"domicilio\":"
				+ DatosFiscalesDePrueba.DOMICILIO_JSON + ",\"regimenFiscal\":\"612\"}";

		mockMvc.perform(datosFiscales(facturista, enFacturacion, corregidos)).andExpect(status().isOk())
				.andExpect(jsonPath("$.datosFiscales.rfc").value("LOHA850101AB2"))
				.andExpect(jsonPath("$.datosFiscales.domicilio.codigoPostal").value("37000"));
		mockMvc.perform(datosFiscales(facturista, enPago, corregidos)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Facturación corrige los datos fiscales mientras el prospecto está en Facturación"));
		mockMvc.perform(datosFiscales(token(vendedor), enFacturacion, corregidos)).andExpect(status().isConflict());
	}

	@Test
	void contabilidadYaNoPasaDePagoAFacturacionSinValidarLosPagos() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario contador = registrar("contabilidad1", Role.CONTABILIDAD);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "PAGO")).andExpect(status().isOk());

		mockMvc.perform(cambioDeEtapa(token(contador), id, "FACTURACION")).andExpect(status().isForbidden());
		assertThat(historialDe(id)).hasSize(2);
	}

	@Test
	void noSeFinalizaSinCorreoSalvoQueElClienteNoQuieraLaApp() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		llevarAEntrega(vendedor, id);

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "FINALIZADO")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Captura el correo del cliente para crear su cuenta de la app, o marca que no la quiere"));
		assertThat(eventos.stream(ProspectoFinalizado.class)).isEmpty();

		mockMvc.perform(finalizar(token(vendedor), id, "{\"etapa\":\"FINALIZADO\",\"clienteRechazaApp\":true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.correo").isEmpty());
		assertThat(eventos.stream(ProspectoFinalizado.class)).singleElement()
				.satisfies(evento -> assertThat(evento.correoCliente()).isNull());
	}

	@Test
	void alFinalizarSePuedeCapturarElCorreoQueFaltaba() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		llevarAEntrega(vendedor, id);

		mockMvc.perform(
				finalizar(token(vendedor), id, "{\"etapa\":\"FINALIZADO\",\"correoCliente\":\"Ana@Ejemplo.com\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.correo").value("ana@ejemplo.com"));
		assertThat(eventos.stream(ProspectoFinalizado.class)).singleElement()
				.satisfies(evento -> assertThat(evento.correoCliente()).isEqualTo("ana@ejemplo.com"));
	}

	@Test
	void rechazaUnCorreoMalEscritoAlFinalizar() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		llevarAEntrega(vendedor, id);

		mockMvc.perform(finalizar(token(vendedor), id, "{\"etapa\":\"FINALIZADO\",\"correoCliente\":\"ana@\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void siElClienteNoQuiereLaAppElEventoSaleSinCorreoAunqueLoTenga() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", "ana@ejemplo.com");
		llevarAEntrega(vendedor, id);

		mockMvc.perform(finalizar(token(vendedor), id, "{\"etapa\":\"FINALIZADO\",\"clienteRechazaApp\":true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.correo").value("ana@ejemplo.com"));

		assertThat(eventos.stream(ProspectoFinalizado.class)).singleElement()
				.satisfies(evento -> assertThat(evento.correoCliente()).isNull());
	}

	@Test
	void alFinalizarSePublicaUnSoloProspectoFinalizado() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", "Ana@Ejemplo.com");
		llevarAEntrega(vendedor, id);
		assertThat(eventos.stream(ProspectoFinalizado.class)).isEmpty();

		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "FINALIZADO")).andExpect(status().isOk());

		assertThat(eventos.stream(ProspectoFinalizado.class)).singleElement().satisfies(evento -> {
			assertThat(evento.prospectoId()).isEqualTo(id);
			assertThat(evento.vendedorId()).isEqualTo(vendedor.getId());
			assertThat(evento.nombre()).isEqualTo("Ana López");
			assertThat(evento.correoCliente()).isEqualTo("ana@ejemplo.com");
			assertThat(evento.fechaEntrega()).isNotNull();
		});
	}

	@Test
	void facturacionYaNoPasaAEntregaPorElPatch() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		Usuario facturista = llevarAFacturacion(vendedor, id);

		mockMvc.perform(cambioDeEtapa(token(facturista), id, "ENTREGA")).andExpect(status().isForbidden());
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("select etapa from prospecto where id = ?", String.class, id))
				.isEqualTo("FACTURACION");
	}

	@Test
	void contabilidadNoCambiaEtapasPorElPatch() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario contador = registrar("contabilidad1", Role.CONTABILIDAD);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);

		mockMvc.perform(cambioDeEtapa(token(contador), id, "VISITA")).andExpect(status().isForbidden());
		mockMvc.perform(cambioDeEtapa(token(contador), id, "FACTURACION")).andExpect(status().isForbidden());
	}

	/**
	 * Lleva el prospecto a Entrega: el vendedor lo pasa a Pago, Caja registra y
	 * envía un pago, Contabilidad lo valida (ERP-1.1) y la factura emitida lo pasa
	 * a Entrega (ERP-1.2).
	 */
	private void llevarAEntrega(Usuario vendedor, long id) throws Exception {
		Usuario facturista = llevarAFacturacion(vendedor, id);
		autorizarEntrega(id, facturista);
	}

	/**
	 * Lo que hace ventascrm al recibir FacturaEmitida. Aquí se llama directo porque
	 * la prueba corre en una transacción y el evento llega después del commit; el
	 * evento real se prueba en EntregaAlFacturarIntegrationTest.
	 */
	private void autorizarEntrega(long id, Usuario facturista) {
		entityManager.flush();
		prospectoRepository.findById(id).orElseThrow().autorizarEntregaPorFactura(Instant.now(), facturista.getId());
		entityManager.flush();
	}

	/** Hasta Facturación por el camino real; regresa a quien factura. */
	private Usuario llevarAFacturacion(Usuario vendedor, long id) throws Exception {
		Usuario cajera = registrar("caja-" + id, Role.CAJA);
		Usuario contador = registrar("conta-" + id, Role.CONTABILIDAD);
		Usuario facturista = registrar("factura-" + id, Role.FACTURACION);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "PAGO")).andExpect(status().isOk());
		mockMvc.perform(conToken(post("/api/v1/liquidaciones/{id}/pagos", id), token(cajera))
				.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":489900,\"fechaPago\":\"2026-10-16\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(conToken(post("/api/v1/liquidaciones/{id}/enviar", id), token(cajera)))
				.andExpect(status().isOk());
		mockMvc.perform(conToken(post("/api/v1/liquidaciones/{id}/validar", id), token(contador)))
				.andExpect(status().isOk());
		return facturista;
	}

	@Test
	void unFinalizadoMuestraElVehiculoQueEligioFacturacion() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario almacen = registrar("inventario1", Role.INVENTARIO);
		String alta = mockMvc
				.perform(conToken(post("/api/v1/vehiculos"), token(almacen)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"vin\":\"3N1AB8CV5PY177777\",\"modelo\":\"Sentra\",\"version\":\"Advance\","
								+ "\"anio\":2026,\"color\":\"Blanco\",\"precio\":489900.00}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long sentra = idDe(alta);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", "ana@ejemplo.com");
		Usuario facturista = llevarAFacturacion(vendedor, id);
		// Desde ERP-1.3 la unidad se vende al emitir la factura.
		ventaDeUnidades.vender(id, sentra, facturista.getId(), Instant.now());
		autorizarEntrega(id, facturista);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "FINALIZADO")).andExpect(status().isOk());

		mockMvc.perform(conToken(get(URL), token(vendedor))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].etapa").value("FINALIZADO"))
				.andExpect(jsonPath("$[0].vehiculo.vin").value("3N1AB8CV5PY177777"))
				.andExpect(jsonPath("$[0].vehiculo.modelo").value("Sentra"))
				.andExpect(jsonPath("$[0].vehiculo.anio").value(2026));
	}

	private static MockHttpServletRequestBuilder finalizar(String token, long id, String cuerpo) {
		return conToken(patch(URL + "/" + id + "/etapa"), token).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo);
	}

	@Test
	void laListaMuestraElUltimoCambioDeEtapa() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "VISITA")).andExpect(status().isOk());

		mockMvc.perform(conToken(get(URL), token(vendedor))).andExpect(jsonPath("$[0].ultimoCambio").isNotEmpty())
				.andExpect(jsonPath("$[0].cierrePendiente").value(false));
	}

	@Test
	void elHistorialDiceQuienHizoCadaCambio() throws Exception {
		Usuario vendedor = registrar("vendedor1", Role.VENTAS);
		Usuario gerente = registrar("gerente1", Role.GERENTE_MARKETING);
		long id = crearProspecto(vendedor, "Ana López", "4771234567", null);
		mockMvc.perform(cambioDeEtapa(token(vendedor), id, "VISITA")).andExpect(status().isOk());

		mockMvc.perform(conToken(get(URL + "/{id}/historial", id), token(gerente))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].etapa").value("PRIMER_CONTACTO"))
				.andExpect(jsonPath("$[1].etapa").value("VISITA"))
				.andExpect(jsonPath("$[1].usuarioNombre").value("Persona vendedor1"))
				.andExpect(jsonPath("$[1].fechaHora").isNotEmpty());
	}

	@Test
	void unVendedorNoVeElHistorialDeOtro() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		long id = crearProspecto(vendedor1, "Ana López", "4771234567", null);

		mockMvc.perform(conToken(get(URL + "/{id}/historial", id), token(vendedor2))).andExpect(status().isNotFound());
	}

	@Test
	void avisaDeCoincidenciasMientrasSeCapturaSinDecirDeQuien() throws Exception {
		Usuario vendedor1 = registrar("vendedor1", Role.VENTAS);
		Usuario vendedor2 = registrar("vendedor2", Role.VENTAS);
		crearProspecto(vendedor1, "Ana López", "4771234567", "ana@ejemplo.com");

		mockMvc.perform(conToken(get(URL + "/coincidencias").param("telefono", "477 123 4567"), token(vendedor2)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.contactoRepetido").value(true))
				.andExpect(jsonPath("$.nombre").doesNotExist());
		mockMvc.perform(conToken(get(URL + "/coincidencias").param("correo", "otra@ejemplo.com"), token(vendedor2)))
				.andExpect(jsonPath("$.contactoRepetido").value(false));
	}

	private Usuario registrar(String usuario, Role rol) {
		Rol encontrado = rolRepository.findByNombre(rol.name()).orElseThrow();
		return usuarioRepository.save(new Usuario(usuario, "hash-de-prueba", "Persona " + usuario, encontrado));
	}

	private String token(Usuario usuario) {
		return jwtService.generateToken(usuario.getId(), usuario.getUsuario(), List.of(usuario.getRol().comoRole()));
	}

	private long crearProspecto(Usuario vendedor, String nombre, String telefono, String correo) throws Exception {
		String cuerpo = """
				{"nombre":"%s","telefono":%s,"correo":%s}
				""".formatted(nombre, json(telefono), json(correo));
		String respuesta = mockMvc.perform(registro(token(vendedor), cuerpo)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idDe(respuesta);
	}

	/** Escribe en la base lo pendiente de JPA para poder leerlo con SQL. */
	private List<Map<String, Object>> historialDe(long prospectoId) {
		entityManager.flush();
		return jdbcTemplate.queryForList(
				"select etapa, origen, fecha_hora from historial_etapa where prospecto_id = ? order by id",
				prospectoId);
	}

	private static MockHttpServletRequestBuilder registro(String token, String cuerpo) {
		return conToken(post(URL), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	/**
	 * Al pasar a Pago el vendedor manda el precio total (decisión del PO, 19 de
	 * octubre) y los datos fiscales del cliente (21 de octubre).
	 */
	private static MockHttpServletRequestBuilder cambioDeEtapa(String token, long id, String etapa) {
		if ("PAGO".equals(etapa)) {
			return conToken(patch(URL + "/" + id + "/etapa"), token).contentType(MediaType.APPLICATION_JSON).content(
					"{\"etapa\":\"PAGO\",\"precioTotal\":489900,\"datosFiscales\":" + DatosFiscalesDePrueba.JSON + "}");
		}
		return cambioDeEtapaSinPrecio(token, id, etapa);
	}

	private static MockHttpServletRequestBuilder datosFiscales(String token, long id, String cuerpo) {
		return conToken(put(URL + "/{id}/datos-fiscales", id), token).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo);
	}

	private static MockHttpServletRequestBuilder cambioDeEtapaSinPrecio(String token, long id, String etapa) {
		return conToken(patch(URL + "/{id}/etapa", id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"" + etapa + "\"}");
	}

	private static MockHttpServletRequestBuilder cierre(String token, long id, String motivo) {
		return conToken(patch(URL + "/{id}/etapa", id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"etapa\":\"CERRADO\",\"motivoCierre\":\"" + motivo + "\"}");
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static long idDe(String respuesta) {
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private static String json(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}
}
