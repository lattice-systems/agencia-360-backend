package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AvisoAlCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ParametroTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.FranjaHorarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ParametroTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Valuación formal de punta a punta (V2-M26.01 a V2-M26.04) contra PostgreSQL
 * real. No revierte cada prueba porque el prospecto se crea después del commit:
 * cada una siembra sus clientes y su marca, y al terminar borra sus citas para
 * no gastar el cupo de otras pruebas.
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class ValuacionIntegrationTest extends AbstractIntegrationTest {

	private static final String RUTA = "/api/v1/app/valuaciones";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private ParametroTomaCuentaRepository parametros;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FranjaHorarioRepository franjas;

	private static final ZoneId ZONA_AGENCIA = ZoneId.of("America/Mexico_City");

	@MockitoBean
	private EnvioCorreos envioCorreos;

	@Autowired
	private ApplicationEvents eventos;

	private record Cliente(long id, String correo, long vehiculo, String token) {
	}

	private final List<Long> sembrados = new ArrayList<>();
	private String unico;
	private String marca;
	private Cliente ana;
	private Cliente beto;

	@BeforeEach
	void sembrar() {
		unico = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		marca = "M" + unico;
		parametros.save(ParametroTomaCuenta.nuevo(marca, "March", 2020, new BigDecimal("200000"),
				new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.10")));
		usuario("vendedor-" + unico, "VENTAS", "Vera Vendedora");
		ana = cliente("ana", "A");
		beto = cliente("beto", "B");
	}

	@AfterEach
	void liberarLaAgenda() {
		for (Long cliente : sembrados) {
			jdbc.update("delete from valuacion where cliente_id = ?", cliente);
			jdbc.update("delete from cita where cliente_id = ?", cliente);
		}
	}

	@Test
	void pedirLaValuacionLaCreaSolicitadaYRepetirDevuelveLaMisma() throws Exception {
		long estimacion = estimar(ana);

		String creada = pedir(ana, estimacion).andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("SOLICITADA")).andExpect(jsonPath("$.vehiculoId").value(ana.vehiculo()))
				.andExpect(jsonPath("$.estimacion.id").value(estimacion)).andExpect(jsonPath("$.fotos.length()").value(0))
				.andExpect(jsonPath("$.puedeAgendar").value(true)).andExpect(jsonPath("$.cita").isEmpty())
				.andExpect(jsonPath("$.oferta").isEmpty()).andExpect(jsonPath("$.creadaEn").isString()).andReturn()
				.getResponse().getContentAsString();
		long id = numero(creada, "$.id");

		pedir(ana, estimar(ana)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
		assertThat(jdbc.queryForObject("select count(*) from valuacion where cliente_id = ?", Integer.class, ana.id()))
				.isEqualTo(1);
	}

	@Test
	void elProspectoNaceConOrigenAppYElAsesorApareceEnLaValuacion() throws Exception {
		long id = solicitada(ana);

		long prospecto = esperarProspecto(id);

		assertThat(jdbc.queryForObject("select origen from historial_etapa where prospecto_id = ? order by id limit 1",
				String.class, prospecto)).isEqualTo("APP");
		assertThat(jdbc.queryForObject("select correo from prospecto where id = ?", String.class, prospecto))
				.isEqualTo(ana.correo());
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.asesor").isNotEmpty());
	}

	@Test
	void reutilizaElProspectoAbiertoDeEstreno() throws Exception {
		long modelo = jdbc.queryForObject("""
				insert into modelo_catalogo (marca, modelo, anio, version, precio_desde, activo, created_at, updated_at)
				values ('Nissan', ?, 2026, 'Advance', 300000, true, now(), now()) returning id
				""", Long.class, "Kicks" + unico);
		comoCliente(ana, json(post("/api/v1/app/estreno/solicitudes"), "{\"tipo\":\"COTIZACION\",\"modeloCatalogoId\":"
				+ modelo + ",\"enganche\":60000,\"plazoMeses\":48}")).andExpect(status().isCreated());
		long delEstreno = esperar("select prospecto_id from solicitud_estreno where cliente_id = ?", ana.id());

		long id = solicitada(ana);

		assertThat(jdbc.queryForObject("select prospecto_id from valuacion where id = ?", Long.class, id))
				.isEqualTo(delEstreno);
	}

	@Test
	void unaEstimacionSinVehiculoDa422YUnaAjena404() throws Exception {
		String aMano = comoCliente(ana, json(post("/api/v1/app/toma-a-cuenta/estimaciones"),
				"{\"marca\":\"" + marca + "\",\"modelo\":\"March\",\"anio\":2020,\"km\":1000,\"estado\":\"BUENO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		pedir(ana, numero(aMano, "$.id")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.codigo").value("VALUACION_SIN_VEHICULO"));
		pedir(ana, estimar(beto)).andExpect(status().isNotFound());
		pedir(ana, Long.MAX_VALUE).andExpect(status().isNotFound());
		comoCliente(ana, json(post(RUTA), "{}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void siElVehiculoYaNoEsDelClienteDa404() throws Exception {
		long estimacion = estimar(ana);
		jdbc.update("update vehiculo_cliente set cliente_id = ? where id = ?", beto.id(), ana.vehiculo());

		pedir(ana, estimacion).andExpect(status().isNotFound());
	}

	@Test
	void laListaEsSoloDelClienteYLoAjenoDa404() throws Exception {
		long deAna = solicitada(ana);
		solicitada(beto);

		comoCliente(ana, get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(deAna));
		comoCliente(ana, get(RUTA + "?tamano=51")).andExpect(status().isBadRequest());
		comoCliente(beto, get(RUTA + "/{id}", deAna)).andExpect(status().isNotFound());
		comoCliente(ana, get(RUTA + "/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
	}

	@Test
	void sinTokenDa401YElPersonalOUnaCuentaDesactivada403() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		mockMvc.perform(json(post(RUTA), "{\"estimacionTomaCuentaId\":1}")).andExpect(status().isUnauthorized());
		String gerente = jwtService.generateToken(ana.id(), "gerente", List.of(Role.VENTAS));
		mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + gerente))
				.andExpect(status().isForbidden());

		cuentasAppService.desactivar(ana.id());
		comoCliente(ana, get(RUTA)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
	}

	@Test
	void lasFotosSeGuardanSinExifSeReemplazanYSeBajanSoloConLaUrlFirmada() throws Exception {
		long id = solicitada(ana);
		byte[] conExif = jpegConExif();
		assertThat(new String(conExif, StandardCharsets.ISO_8859_1)).contains("Exif", "GPS-SECRETO");

		String primera = subir(ana, id, "TABLERO_KM", conExif).andExpect(status().isOk())
				.andExpect(jsonPath("$.fotos.length()").value(1)).andReturn().getResponse().getContentAsString();
		String urlVieja = JsonPath.read(primera, "$.fotos[0].url");
		subir(ana, id, "FRENTE", jpegConExif()).andExpect(status().isOk());
		String ultima = subir(ana, id, "TABLERO_KM", conExif).andExpect(status().isOk())
				.andExpect(jsonPath("$.fotos.length()").value(2)).andExpect(jsonPath("$.fotos[0].toma").value("FRENTE"))
				.andExpect(jsonPath("$.fotos[1].toma").value("TABLERO_KM"))
				.andExpect(jsonPath("$.fotos[1].subidaEn").isString()).andReturn().getResponse().getContentAsString();

		String url = JsonPath.read(ultima, "$.fotos[1].url");
		byte[] descargada = mockMvc.perform(get(URI.create(url))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsByteArray();
		assertThat(new String(descargada, StandardCharsets.ISO_8859_1)).doesNotContain("Exif", "GPS-SECRETO");
		String alterada = url.substring(0, url.length() - 1) + (url.endsWith("0") ? "1" : "0");
		mockMvc.perform(get(URI.create(alterada))).andExpect(status().isNotFound());
		// La reemplazada ya no está en el almacén.
		mockMvc.perform(get(URI.create(urlVieja))).andExpect(status().isNotFound());
		assertThat(ultima).doesNotContain("\"clave\"");
	}

	@Test
	void unaFotoInvalidaDa400YEnUnaValuacionAjena404() throws Exception {
		long id = solicitada(ana);
		byte[] gif = imagen("gif");
		byte[] webp = "RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
		byte[] gigante = Arrays.copyOf(imagen("jpg"), 8 * 1024 * 1024 + 1);

		for (byte[] mala : List.of(webp, gif, "no es una foto".getBytes(StandardCharsets.UTF_8), gigante)) {
			subir(ana, id, "FRENTE", mala).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}
		subir(ana, id, "TECHO", imagen("jpg")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		comoCliente(ana, multipart(RUTA + "/{id}/fotos/{toma}", id, "FRENTE")).andExpect(status().isBadRequest());
		subir(beto, id, "FRENTE", imagen("jpg")).andExpect(status().isNotFound());
		subir(ana, id, "FRENTE", imagen("png")).andExpect(status().isOk());
		mockMvc.perform(multipart(RUTA + "/{id}/fotos/{toma}", id, "FRENTE")).andExpect(status().isUnauthorized());
	}

	@Test
	void unaValuacionCerradaYaNoAdmiteFotos() throws Exception {
		long id = solicitada(ana);
		jdbc.update("""
				update valuacion set estado = 'OFERTADA', oferta = 100000, vigencia_hasta = current_date + 5,
				  ofertada_en = now() where id = ?
				""", id);

		subir(ana, id, "FRENTE", imagen("jpg")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("VALUACION_CERRADA"));
	}

	@Test
	void laInspeccionUsaLaAgendaConMotivoValuacionYAlCancelarlaVuelveASolicitada() throws Exception {
		long id = solicitada(ana);
		LocalDate dia = diaHabil(40);

		String cita = agendar(ana, id, franja(dia), dia).andExpect(status().isCreated())
				.andExpect(jsonPath("$.motivo").value("valuacion"))
				.andExpect(jsonPath("$.vehiculoId").value(ana.vehiculo())).andReturn().getResponse()
				.getContentAsString();
		long citaId = numero(cita, "$.id");

		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.estado").value("CITA_AGENDADA"))
				.andExpect(jsonPath("$.puedeAgendar").value(false)).andExpect(jsonPath("$.cita.citaId").value(citaId))
				.andExpect(jsonPath("$.cita.fecha").value(dia.toString()))
				.andExpect(jsonPath("$.cita.horaInicio").value(org.hamcrest.Matchers.matchesPattern("\\d{2}:\\d{2}")));
		LocalDate otro = diaHabil(45);
		agendar(ana, id, franja(otro), otro).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("VALUACION_NO_AGENDABLE"));
		// Reprogramar conserva el id de la cita: la valuación no cambia.
		comoCliente(ana, json(post("/api/v1/app/citas/{id}/reprogramar", citaId),
				"{\"franjaId\":" + franja(otro) + ",\"fecha\":\"" + otro + "\"}")).andExpect(status().isOk());
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.cita.citaId").value(citaId))
				.andExpect(jsonPath("$.cita.fecha").value(otro.toString()));

		comoCliente(ana, post("/api/v1/app/citas/{id}/cancelar", citaId)).andExpect(status().isOk());

		comoCliente(ana, get(RUTA)).andExpect(jsonPath("$.contenido[0].estado").value("SOLICITADA"))
				.andExpect(jsonPath("$.contenido[0].cita").isEmpty())
				.andExpect(jsonPath("$.contenido[0].puedeAgendar").value(true));
		assertThat(jdbc.queryForObject("select cita_id from valuacion where id = ?", Long.class, id)).isNull();
		agendar(ana, id, franja(dia), dia).andExpect(status().isCreated());
	}

	@Test
	void siLaCitaPasoHaceMasDeSieteDiasSinOfertaSePuedeAgendarOtra() throws Exception {
		long id = solicitada(ana);
		inspeccionSembrada(ana, id, hoy().minusDays(8));
		LocalDate dia = diaHabil(40);

		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.puedeAgendar").value(true));
		agendar(ana, id, franja(dia), dia).andExpect(status().isCreated());
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.cita.fecha").value(dia.toString()))
				.andExpect(jsonPath("$.puedeAgendar").value(false));
	}

	@Test
	void agendarRespetaElCupoLaCitaDuplicadaYElDueno() throws Exception {
		long id = solicitada(ana);
		LocalDate dia = diaHabil(41);
		long franja = franja(dia);

		agendar(beto, id, franja, dia).andExpect(status().isNotFound());
		comoCliente(ana, json(post(RUTA + "/{id}/cita", id), "{}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		agendar(ana, id, 999_999L, dia).andExpect(status().isBadRequest());
		mockMvc.perform(json(post(RUTA + "/{id}/cita", id), "{}")).andExpect(status().isUnauthorized());

		int capacidad = jdbc.queryForObject("select capacidad from franja_horario where id = ?", Integer.class, franja);
		for (int i = 0; i < capacidad; i++) {
			// Cada lugar con un vehículo distinto: uno no puede repetir franja.
			long otroAuto = registroClientesService.asignarVehiculo(beto.id(),
					new VehiculoNuevo(null, ("C" + i + "VA" + unico + "000000000").substring(0, 17).toUpperCase(Locale.ROOT),
							marca, "March", 2020, null))
					.vehiculos().getLast().id();
			citaSembrada(new Cliente(beto.id(), beto.correo(), otroAuto, beto.token()), franja, dia);
		}
		agendar(ana, id, franja, dia).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("FRANJA_SIN_CUPO"));

		LocalDate otro = diaHabil(47);
		citaSembrada(ana, franja(otro), otro);
		agendar(ana, id, franja(otro), otro).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CITA_DUPLICADA"));
		// Nada de lo rechazado dejó la valuación a medias.
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.estado").value("SOLICITADA"));
	}

	@Test
	void citasNoAceptaElMotivoValuacion() throws Exception {
		LocalDate dia = diaHabil(42);

		comoCliente(ana, json(post("/api/v1/app/citas"), "{\"vehiculoId\":" + ana.vehiculo() + ",\"franjaId\":"
				+ franja(dia) + ",\"fecha\":\"" + dia + "\",\"motivo\":\"Valuacion\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		assertThat(jdbc.queryForObject("select count(*) from cita where cliente_id = ?", Integer.class, ana.id()))
				.isZero();
	}

	@Test
	void laOfertaPideLaInspeccionHechaYAvisaSoloAlDuenoSinElMonto() throws Exception {
		long id = solicitada(ana);
		esperarProspecto(id);
		String admin = personal("ADMINISTRADOR", Role.ADMINISTRADOR);

		ofertar(admin, id, "{\"monto\":150000}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		LocalDate dia = diaHabil(43);
		agendar(ana, id, franja(dia), dia).andExpect(status().isCreated());
		ofertar(admin, id, "{\"monto\":150000}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		inspeccionSembrada(ana, id, hoy());

		for (String malo : List.of("{}", "{\"monto\":0}", "{\"monto\":10.001}",
				"{\"monto\":1,\"vigenciaHasta\":\"" + hoy().minusDays(1) + "\"}",
				"{\"monto\":1,\"vigenciaHasta\":\"" + hoy().plusDays(31) + "\"}",
				"{\"monto\":1,\"nota\":\"" + "a".repeat(501) + "\"}")) {
			ofertar(admin, id, malo).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}
		ofertar(admin, id, "{\"monto\":150000,\"nota\":\"Con papeles en regla\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("OFERTADA"))
				.andExpect(jsonPath("$.oferta.monto").value(150000.0))
				.andExpect(jsonPath("$.oferta.vigenciaHasta").value(hoy().plusDays(7).toString()));
		// Una oferta vigente no se edita.
		ofertar(admin, id, "{\"monto\":160000}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

		assertThat(eventos.stream(AvisoAlCliente.class).filter(aviso -> aviso.evento().equals("valuacion.oferta")))
				.singleElement().satisfies(aviso -> {
					assertThat(aviso.clienteId()).isEqualTo(ana.id());
					assertThat(aviso.datos()).containsOnlyKeys("valuacionId", "vehiculoId", "vigenciaHasta")
							.containsEntry("valuacionId", id).containsEntry("vehiculoId", ana.vehiculo())
							.containsEntry("vigenciaHasta", hoy().plusDays(7).toString());
				});
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.oferta.nota").value("Con papeles en regla"))
				.andExpect(jsonPath("$.oferta.ofertadaEn").isString()).andExpect(jsonPath("$.puedeAgendar").value(false));
		assertThat(jdbc.queryForObject("select ofertada_por is not null from valuacion where id = ?", Boolean.class, id))
				.isTrue();
	}

	@Test
	void laOfertaSeRespondeUnaVezConElMontoQueSeVio() throws Exception {
		long id = ofertada(ana);

		responder(beto, id, true, "150000").andExpect(status().isNotFound());
		comoCliente(ana, json(post(RUTA + "/{id}/respuesta", id), "{\"aceptada\":true}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		mockMvc.perform(json(post(RUTA + "/{id}/respuesta", id), "{}")).andExpect(status().isUnauthorized());
		responder(ana, id, true, "149000").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("OFERTA_NO_DISPONIBLE"));
		responder(ana, id, true, "150000.00").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACEPTADA")).andExpect(jsonPath("$.respondidaEn").isString());
		responder(ana, id, true, "150000").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEPTADA"));
		responder(ana, id, false, "150000").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("OFERTA_NO_DISPONIBLE"));
		long sinOferta = solicitada(beto);
		responder(beto, sinOferta, true, "150000").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("OFERTA_NO_DISPONIBLE"));
	}

	@Test
	void unaOfertaPasadaDeVigenciaEstaVencidaYSePuedeRenovarSiNoHayOtraAbierta() throws Exception {
		long id = ofertada(ana);
		String admin = personal("ADMINISTRADOR", Role.ADMINISTRADOR);
		vencer(id);

		responder(ana, id, true, "150000").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("OFERTA_VENCIDA"));
		comoCliente(ana, get(RUTA + "/{id}", id)).andExpect(jsonPath("$.estado").value("VENCIDA"));
		assertThat(estadoGuardado(id)).isEqualTo("VENCIDA");
		ofertar(admin, id, "{\"monto\":140000}").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("OFERTADA"));
		// Con la pantalla vieja abierta no se acepta una cifra que no leyó.
		responder(ana, id, true, "150000").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("OFERTA_NO_DISPONIBLE"));

		vencer(id);
		// Pedir otra guarda la vencida y no choca con el índice de abiertas.
		long otra = solicitada(ana);
		assertThat(estadoGuardado(id)).isEqualTo("VENCIDA");
		assertThat(otra).isNotEqualTo(id);
		ofertar(admin, id, "{\"monto\":140000}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
	}

	@Test
	void ventasVeYOfertaSoloLoDeSusProspectosYElAdministradorTodo() throws Exception {
		long id = ofertada(ana);
		long prospecto = jdbc.queryForObject("select prospecto_id from valuacion where id = ?", Long.class, id);
		long asignado = jdbc.queryForObject("select vendedor_id from prospecto where id = ?", Long.class, prospecto);
		String suVendedor = jwtService.generateToken(asignado, "asignado", List.of(Role.VENTAS));
		String otroVendedor = personal("VENTAS", Role.VENTAS);
		String admin = personal("ADMINISTRADOR", Role.ADMINISTRADOR);

		comoPersonal(suVendedor, get("/api/v1/valuaciones/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.cliente").value("ana " + unico))
				.andExpect(jsonPath("$.estimacion.marca").value(marca))
				.andExpect(jsonPath("$.estimacion.minimo").isNumber()).andExpect(jsonPath("$.cita.citaId").isNumber())
				.andExpect(jsonPath("$.fotos.length()").value(0));
		comoPersonal(otroVendedor, get("/api/v1/valuaciones/{id}", id)).andExpect(status().isNotFound());
		ofertar(otroVendedor, id, "{\"monto\":150000}").andExpect(status().isNotFound());
		comoPersonal(otroVendedor, get("/api/v1/valuaciones")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
		comoPersonal(suVendedor, get("/api/v1/valuaciones?estado=OFERTADA&tamano=50")).andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[?(@.id == " + id + ")]").isNotEmpty());
		comoPersonal(suVendedor, get("/api/v1/valuaciones?estado=ACEPTADA&tamano=50"))
				.andExpect(jsonPath("$.contenido[?(@.id == " + id + ")]").isEmpty());
		comoPersonal(admin, get("/api/v1/valuaciones/{id}", id)).andExpect(status().isOk());
		comoPersonal(admin, get("/api/v1/valuaciones/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
		comoPersonal(admin, get("/api/v1/valuaciones?estado=OTRO")).andExpect(status().isBadRequest());
		comoPersonal(admin, get("/api/v1/valuaciones?tamano=51")).andExpect(status().isBadRequest());
		// Una sin prospecto solo la ve el administrador.
		jdbc.update("update valuacion set prospecto_id = null where id = ?", id);
		comoPersonal(suVendedor, get("/api/v1/valuaciones/{id}", id)).andExpect(status().isNotFound());
		comoPersonal(admin, get("/api/v1/valuaciones/{id}", id)).andExpect(status().isOk());
	}

	@Test
	void lasRutasDelPersonalNiegan401Y403() throws Exception {
		long id = solicitada(ana);
		String servicio = personal("SERVICIO", Role.SERVICIO);

		for (String token : List.of(ana.token(), servicio)) {
			comoPersonal(token, get("/api/v1/valuaciones")).andExpect(status().isForbidden());
			comoPersonal(token, get("/api/v1/valuaciones/{id}", id)).andExpect(status().isForbidden());
			ofertar(token, id, "{\"monto\":1}").andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/api/v1/valuaciones")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/valuaciones/{id}", id)).andExpect(status().isUnauthorized());
		mockMvc.perform(json(post("/api/v1/valuaciones/{id}/oferta", id), "{\"monto\":1}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void elValorEstimadoDaUnPuntoPorMesSoloDelVehiculoPropio() throws Exception {
		String ruta = "/api/v1/app/vehiculos/{id}/valor-estimado";
		comoCliente(ana, get(ruta, ana.vehiculo())).andExpect(status().isOk())
				.andExpect(jsonPath("$.vehiculoId").value(ana.vehiculo()))
				.andExpect(jsonPath("$.puntos.length()").value(0));
		long vieja = estimar(ana);
		long reemplazada = estimar(ana);
		long deEsteMes = estimar(ana);
		estimar(beto);
		jdbc.update("update estimacion_toma_cuenta set created_at = ? where id = ?",
				Timestamp.from(hoy().minusMonths(2).withDayOfMonth(15).atTime(12, 0).atZone(ZONA_AGENCIA).toInstant()),
				vieja);

		comoCliente(ana, get(ruta, ana.vehiculo())).andExpect(status().isOk())
				.andExpect(jsonPath("$.puntos.length()").value(2))
				.andExpect(jsonPath("$.puntos[0].mes").value(YearMonth.from(hoy().minusMonths(2)).toString()))
				.andExpect(jsonPath("$.puntos[0].estimacionTomaCuentaId").value(vieja))
				.andExpect(jsonPath("$.puntos[1].mes").value(YearMonth.from(hoy()).toString()))
				.andExpect(jsonPath("$.puntos[1].estimacionTomaCuentaId").value(deEsteMes))
				.andExpect(jsonPath("$.puntos[1].creadaEn").isString())
				.andExpect(jsonPath("$.puntos[1].minimo").isNumber())
				.andExpect(jsonPath("$.puntos[1].maximo").isNumber())
				.andExpect(jsonPath("$.puntos[1].medio").isNumber()).andExpect(jsonPath("$.puntos[1].km").value(60000))
				.andExpect(jsonPath("$.puntos[1].estado").value("BUENO"));
		assertThat(reemplazada).isNotEqualTo(deEsteMes);
		comoCliente(ana, get(ruta + "?meses=2", ana.vehiculo())).andExpect(jsonPath("$.puntos.length()").value(1));
		comoCliente(ana, get(ruta + "?meses=37", ana.vehiculo())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		comoCliente(ana, get(ruta + "?meses=0", ana.vehiculo())).andExpect(status().isBadRequest());
		comoCliente(beto, get(ruta, ana.vehiculo())).andExpect(status().isNotFound());
		mockMvc.perform(get(ruta, ana.vehiculo())).andExpect(status().isUnauthorized());
		comoPersonal(personal("SERVICIO", Role.SERVICIO), get(ruta, ana.vehiculo())).andExpect(status().isForbidden());
		cuentasAppService.desactivar(ana.id());
		comoCliente(ana, get(ruta, ana.vehiculo())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
	}

	private long ofertada(Cliente cliente) throws Exception {
		long id = solicitada(cliente);
		esperarProspecto(id);
		inspeccionSembrada(cliente, id, hoy());
		ofertar(personal("ADMINISTRADOR", Role.ADMINISTRADOR), id, "{\"monto\":150000}").andExpect(status().isOk());
		return id;
	}

	private void vencer(long id) {
		jdbc.update("update valuacion set vigencia_hasta = ? where id = ?", Date.valueOf(hoy().minusDays(1)), id);
	}

	private String estadoGuardado(long id) {
		return jdbc.queryForObject("select estado from valuacion where id = ?", String.class, id);
	}

	private String personal(String rol, Role role) {
		String nombre = rol.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 8);
		Usuario usuario = usuario(nombre, rol, "Persona de " + rol);
		if (role == Role.VENTAS) {
			// Inactivo para que no le toquen prospectos de otras pruebas.
			jdbc.update("update usuario set activo = false where id = ?", usuario.getId());
		}
		return jwtService.generateToken(usuario.getId(), nombre, List.of(role));
	}

	private ResultActions ofertar(String token, long id, String cuerpo) throws Exception {
		return comoPersonal(token, json(post("/api/v1/valuaciones/{id}/oferta", id), cuerpo));
	}

	private ResultActions responder(Cliente cliente, long id, boolean aceptada, String monto) throws Exception {
		return comoCliente(cliente, json(post(RUTA + "/{id}/respuesta", id),
				"{\"aceptada\":" + aceptada + ",\"monto\":" + monto + "}"));
	}

	private ResultActions comoPersonal(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions agendar(Cliente cliente, long valuacion, long franja, LocalDate dia) throws Exception {
		return comoCliente(cliente, json(post(RUTA + "/{id}/cita", valuacion),
				"{\"franjaId\":" + franja + ",\"fecha\":\"" + dia + "\"}"));
	}

	private long franja(LocalDate dia) {
		return franjas.findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc().stream().filter(franja -> franja.esDe(dia))
				.findFirst().orElseThrow().getId();
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZONA_AGENCIA);
	}

	private static LocalDate diaHabil(int enDias) {
		LocalDate dia = hoy().plusDays(enDias);
		return dia.getDayOfWeek() == DayOfWeek.SUNDAY ? dia.plusDays(1) : dia;
	}

	private long citaSembrada(Cliente cliente, long franja, LocalDate dia) {
		return jdbc.queryForObject("""
				insert into cita (cliente_id, vehiculo_cliente_id, fecha, franja_id, hora_inicio, hora_fin, estado,
				  created_at, updated_at)
				values (?, ?, ?, ?, time '10:00', time '11:00', 'CONFIRMADA', now(), now()) returning id
				""", Long.class, cliente.id(), cliente.vehiculo(), Date.valueOf(dia), franja);
	}

	/**
	 * Una inspección en una fecha que la agenda ya no deja pedir, para probar lo
	 * que pasa después de ella.
	 */
	private long inspeccionSembrada(Cliente cliente, long valuacion, LocalDate dia) {
		long cita = citaSembrada(cliente, franja(diaHabil(3)), dia);
		jdbc.update("update valuacion set estado = 'CITA_AGENDADA', cita_id = ? where id = ?", cita, valuacion);
		return cita;
	}

	private ResultActions subir(Cliente cliente, long valuacion, String toma, byte[] imagen) throws Exception {
		return comoCliente(cliente, multipart(RUTA + "/{id}/fotos/{toma}", valuacion, toma)
				.file(new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", imagen)));
	}

	private static byte[] imagen(String formato) throws Exception {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), formato, salida);
		return salida.toByteArray();
	}

	/** Un JPEG con un segmento APP1 Exif, como el que escribe un teléfono. */
	private static byte[] jpegConExif() throws Exception {
		byte[] jpeg = imagen("jpg");
		byte[] datos = "Exif  GPS-SECRETO".getBytes(StandardCharsets.US_ASCII);
		datos[4] = 0;
		datos[5] = 0;
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.write(jpeg, 0, 2);
		salida.write(new byte[]{(byte) 0xFF, (byte) 0xE1, 0, (byte) (datos.length + 2)});
		salida.write(datos);
		salida.write(jpeg, 2, jpeg.length - 2);
		return salida.toByteArray();
	}

	private Cliente cliente(String nombre, String letra) {
		String correo = nombre + "-" + unico + "@correo.com";
		long id = registroClientesService.registrar(new NuevoCliente(nombre + " " + unico, correo, null, null)).id();
		sembrados.add(id);
		String vin = (letra + "VAL" + unico + "000000000").substring(0, 17).toUpperCase(Locale.ROOT);
		long vehiculo = registroClientesService
				.asignarVehiculo(id, new VehiculoNuevo(null, vin, marca, "March", 2020, null)).vehiculos().getFirst()
				.id();
		cuentasAppService.abrir(id, "Temporal123");
		return new Cliente(id, correo, vehiculo,
				jwtService.generateToken(id, correo, List.of(Role.CLIENTE_CARECAR)));
	}

	private Usuario usuario(String nombreUsuario, String rol, String nombre) {
		return usuarioRepository.save(
				new Usuario(nombreUsuario, "hash-de-prueba", nombre, rolRepository.findByNombre(rol).orElseThrow()));
	}

	private long estimar(Cliente cliente) throws Exception {
		return numero(comoCliente(cliente, json(post("/api/v1/app/toma-a-cuenta/estimaciones"),
				"{\"vehiculoId\":" + cliente.vehiculo() + ",\"km\":60000,\"estado\":\"BUENO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions pedir(Cliente cliente, long estimacion) throws Exception {
		return comoCliente(cliente, json(post(RUTA), "{\"estimacionTomaCuentaId\":" + estimacion + "}"));
	}

	private long solicitada(Cliente cliente) throws Exception {
		return numero(pedir(cliente, estimar(cliente)).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id");
	}

	private long esperarProspecto(long valuacion) throws InterruptedException {
		return esperar("select prospecto_id from valuacion where id = ?", valuacion);
	}

	private long esperar(String consulta, long id) throws InterruptedException {
		for (int intento = 0; intento < 100; intento++) {
			Long valor = jdbc.queryForObject(consulta, Long.class, id);
			if (valor != null) {
				return valor;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("El prospecto no se ligó a tiempo");
	}

	private static long numero(String cuerpo, String ruta) {
		return JsonPath.<Number>read(cuerpo, ruta).longValue();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private ResultActions comoCliente(Cliente cliente, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()));
	}
}
