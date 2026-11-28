package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos;
import dev.latticesystems.erp_agente360.backend_agencia360.archivos.application.ArchivosService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DocumentoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.OrigenDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoDocumento;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.DocumentoVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.VehiculoNuevo;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2-M09.01: documentos del vehículo con su estado, sobre la base real migrada.
 */
@AutoConfigureMockMvc
@Transactional
class DocumentosAppIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private DocumentoVehiculoRepository documentos;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	@MockitoBean
	private ArchivosService archivos;

	private String token;
	private Long vehiculoId;
	private Long vehiculoAjenoId;

	@BeforeEach
	void preparar() {
		Long cliente = registroClientesService.registrar(new NuevoCliente("Rosa Ibarra", "rosa@correo.com", null, null))
				.id();
		cuentasAppService.abrir(cliente, "Temporal123");
		token = jwtService.generateToken(cliente, "rosa@correo.com", List.of(Role.CLIENTE_CARECAR));
		vehiculoId = registroClientesService
				.asignarVehiculo(cliente, new VehiculoNuevo(null, "3N1AB8CV5PY900001", "Nissan", "Sentra", 2026, null))
				.vehiculos().getFirst().id();
		Long otro = registroClientesService.registrar(new NuevoCliente("Luis Gil", "luis@correo.com", null, null)).id();
		vehiculoAjenoId = registroClientesService
				.asignarVehiculo(otro, new VehiculoNuevo(null, "3N1AB8CV5PY900002", "Nissan", "Versa", 2026, null))
				.vehiculos().getFirst().id();
	}

	@Test
	void listaConEstadoCalculadoYAseguradoraDeLaPoliza() throws Exception {
		LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Mexico_City"));
		documentos.save(new DocumentoVehiculo(vehiculoId, TipoDocumento.FACTURA, null, "A-1", hoy.minusDays(10), null,
				OrigenDocumento.AGENCIA, null, null));
		documentos.save(new DocumentoVehiculo(vehiculoId, TipoDocumento.VERIFICACION, null, null, null,
				hoy.minusDays(2), OrigenDocumento.CLIENTE, null, null));
		documentos.save(new DocumentoVehiculo(vehiculoId, TipoDocumento.POLIZA, null, "POL-9", null, hoy.plusDays(20),
				OrigenDocumento.CLIENTE, "Seguros Ejemplo", "+52 800 000 1234"));
		documentos.save(new DocumentoVehiculo(vehiculoId, TipoDocumento.OTRO, "Permiso de carga", null, null,
				hoy.plusDays(200), OrigenDocumento.CLIENTE, null, null));

		mockMvc.perform(get("/api/v1/app/vehiculos/" + vehiculoId + "/documentos").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[0].tipo").value("VERIFICACION"))
				.andExpect(jsonPath("$[0].estado").value("VENCIDO")).andExpect(jsonPath("$[0].diasRestantes").value(-2))
				.andExpect(jsonPath("$[1].tipo").value("POLIZA")).andExpect(jsonPath("$[1].estado").value("POR_VENCER"))
				.andExpect(jsonPath("$[1].aseguradora").value("Seguros Ejemplo"))
				.andExpect(jsonPath("$[1].telefonoAseguradora").value("+52 800 000 1234"))
				.andExpect(jsonPath("$[2].titulo").value("Permiso de carga"))
				.andExpect(jsonPath("$[2].estado").value("VIGENTE")).andExpect(jsonPath("$[3].tipo").value("FACTURA"))
				.andExpect(jsonPath("$[3].estado").value("SIN_FECHA"))
				.andExpect(jsonPath("$[3].origen").value("AGENCIA"))
				.andExpect(jsonPath("$[3].tieneArchivo").value(false))
				.andExpect(jsonPath("$[0].archivoUrl").doesNotExist());
	}

	@Test
	void sinDocumentosResponde200ConListaVacia() throws Exception {
		mockMvc.perform(get("/api/v1/app/vehiculos/" + vehiculoId + "/documentos").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unVehiculoAjenoResponde404() throws Exception {
		documentos.save(new DocumentoVehiculo(vehiculoAjenoId, TipoDocumento.LICENCIA, null, null, null, null,
				OrigenDocumento.CLIENTE, null, null));

		mockMvc.perform(get("/api/v1/app/vehiculos/" + vehiculoAjenoId + "/documentos")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNotFound());
	}

	@Test
	void unVehiculoQueNoExisteResponde404() throws Exception {
		mockMvc.perform(
				get("/api/v1/app/vehiculos/999999/documentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mockMvc.perform(get("/api/v1/app/vehiculos/" + vehiculoId + "/documentos"))
				.andExpect(status().isUnauthorized());
	}
	@Test
	void fotoPropiaSeAdjuntaYElArchivoSeAutorizaPorCuenta() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
		byte[] contenido = bytes.toByteArray();
		MockMultipartFile foto = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", contenido);
		when(archivos.guardar(eq("documentos-vehiculo"), eq("image/jpeg"), any(byte[].class)))
				.thenReturn(new AlmacenArchivos.ArchivoGuardado("foto-de-prueba", "image/jpeg", contenido.length));
		when(archivos.urlTemporal("foto-de-prueba")).thenReturn("https://archivos.example/descarga");

		mockMvc.perform(multipart("/api/v1/app/vehiculos/{id}/documentos", vehiculoAjenoId).file(foto)
				.param("tipo", "POLIZA").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
		String cuerpo = mockMvc
				.perform(multipart("/api/v1/app/vehiculos/{id}/documentos", vehiculoId).file(foto)
						.param("tipo", "POLIZA").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.tieneArchivo").value(true))
				.andExpect(jsonPath("$.origen").value("CLIENTE")).andReturn().getResponse().getContentAsString();
		long id = com.jayway.jsonpath.JsonPath.<Number>read(cuerpo, "$.id").longValue();

		mockMvc.perform(
				get("/api/v1/app/documentos/{id}/archivo", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "https://archivos.example/descarga"));
	}

	@Test
	void facturaPdfPublicadaPorAgenciaApareceEnElListadoExistente() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (PDDocument pdf = new PDDocument()) {
			pdf.addPage(new PDPage());
			pdf.save(bytes);
		}
		byte[] contenido = bytes.toByteArray();
		MockMultipartFile factura = new MockMultipartFile("archivo", "factura.pdf", "application/pdf", contenido);
		when(archivos.guardar(eq("documentos-vehiculo"), eq("application/pdf"), any(byte[].class))).thenReturn(
				new AlmacenArchivos.ArchivoGuardado("factura-de-prueba", "application/pdf", contenido.length));
		String personal = jwtService.generateToken(999L, "personal@correo.com", List.of(Role.ADMINISTRADOR));

		mockMvc.perform(multipart("/api/v1/agencia/vehiculos/{id}/documentos", vehiculoId).file(factura)
				.param("tipo", "FACTURA").header(HttpHeaders.AUTHORIZATION, "Bearer " + personal))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.origen").value("AGENCIA"));
		mockMvc.perform(get("/api/v1/app/vehiculos/{id}/documentos", vehiculoId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].tipo").value("FACTURA"))
				.andExpect(jsonPath("$[0].tieneArchivo").value(true));
	}

}
