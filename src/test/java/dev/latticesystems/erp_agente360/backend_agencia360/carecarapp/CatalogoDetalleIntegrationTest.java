package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Rol;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.domain.Usuario;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.RolRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.acceso.infrastructure.UsuarioRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.EnvioCorreos;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EspecificacionModeloCatalogo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ImagenModeloCatalogo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EspecificacionModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ImagenModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.JwtService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle de un modelo del catálogo (V2-M03.02) con la migración V67 y base
 * real.
 */
@AutoConfigureMockMvc
@Transactional
class CatalogoDetalleIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private RolRepository rolRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ImagenModeloCatalogoRepository imagenes;

	@Autowired
	private EspecificacionModeloCatalogoRepository especificaciones;

	@MockitoBean
	private EnvioCorreos envioCorreos;

	private String tokenDeCliente;
	private String tokenDeGerente;
	private String marca;

	@BeforeEach
	void prepararTokens() {
		Long cliente = registroClientesService.registrar(new NuevoCliente("Dana Soto", "dana@correo.com", null, null))
				.id();
		cuentasAppService.abrir(cliente, "Temporal123");
		tokenDeCliente = jwtService.generateToken(cliente, "dana@correo.com", List.of(Role.CLIENTE_CARECAR));
		Rol rol = rolRepository.findByNombre("GERENTE_MARKETING").orElseThrow();
		Usuario gerente = usuarioRepository.save(new Usuario("gerente-detalle", "hash-de-prueba", "Gerente", rol));
		tokenDeGerente = jwtService.generateToken(gerente.getId(), gerente.getUsuario(), List.of(rol.comoRole()));
		// El catálogo es global: cada prueba usa una marca propia.
		marca = "Marca" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	@Test
	void respondeConGaleriaVersionesYFichaOrdenadas() throws Exception {
		int caro = agregar("Kicks", 2026, "Exclusive", 489900);
		int barato = agregar("Kicks", 2026, "Sense", 389900);
		int sinPrecio = agregar("Kicks", 2026, "Edicion", null);
		int retirada = agregar("Kicks", 2026, "Vieja", 100000);
		agregar("Kicks", 2025, "Otro año", 300000);
		mockMvc.perform(post("/api/v1/catalogo/{id}/retirar", retirada).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeGerente)).andExpect(status().isOk());
		imagenes.save(ImagenModeloCatalogo.nueva((long) caro, "https://example.com/2.jpg", "Rojo", "Lateral", 1));
		imagenes.save(ImagenModeloCatalogo.nueva((long) caro, "https://example.com/1.jpg", "Blanco", "Frente", 0));
		especificaciones.save(EspecificacionModeloCatalogo.nueva((long) caro, "Transmisión", "CVT", 1));
		especificaciones.save(EspecificacionModeloCatalogo.nueva((long) caro, "Motor", "1.6 L", 0));

		mockMvc.perform(
				get("/api/v1/app/catalogo/{id}", caro).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(caro))
				.andExpect(jsonPath("$.marca").value(marca)).andExpect(jsonPath("$.version").value("Exclusive"))
				.andExpect(jsonPath("$.activo").value(true)).andExpect(jsonPath("$.imagenes.length()").value(2))
				.andExpect(jsonPath("$.imagenes[0].url").value("https://example.com/1.jpg"))
				.andExpect(jsonPath("$.imagenes[0].color").value("Blanco"))
				.andExpect(jsonPath("$.imagenes[1].orden").value(1))
				.andExpect(jsonPath("$.versiones.length()").value(3))
				.andExpect(jsonPath("$.versiones[0].id").value(barato))
				.andExpect(jsonPath("$.versiones[0].nombre").value("Sense"))
				.andExpect(jsonPath("$.versiones[1].id").value(caro))
				.andExpect(jsonPath("$.versiones[2].id").value(sinPrecio))
				.andExpect(jsonPath("$.especificaciones.length()").value(2))
				.andExpect(jsonPath("$.especificaciones[0].etiqueta").value("Motor"))
				.andExpect(jsonPath("$.especificaciones[1].valor").value("CVT"));
	}

	@Test
	void sinGaleriaNiFichaLasListasVienenVaciasYLaVersionSeIncluye() throws Exception {
		int id = agregar("Sentra", 2026, null, null);

		mockMvc.perform(
				get("/api/v1/app/catalogo/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente))
				.andExpect(status().isOk()).andExpect(jsonPath("$.imagenes").isEmpty())
				.andExpect(jsonPath("$.especificaciones").isEmpty())
				.andExpect(jsonPath("$.versiones.length()").value(1)).andExpect(jsonPath("$.versiones[0].id").value(id))
				.andExpect(jsonPath("$.versiones[0].nombre").value("Sentra"));
	}

	@Test
	void unModeloRetiradoOInexistenteResponde404() throws Exception {
		int id = agregar("Versa", 2026, null, 300000);
		mockMvc.perform(
				post("/api/v1/catalogo/{id}/retirar", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeGerente))
				.andExpect(status().isOk());

		mockMvc.perform(
				get("/api/v1/app/catalogo/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeCliente))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/app/catalogo/{id}", 999999999L).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenDeCliente)).andExpect(status().isNotFound());
	}

	@Test
	void sinTokenResponde401YElPersonalNoEntra() throws Exception {
		int id = agregar("Frontier", 2026, null, null);

		mockMvc.perform(get("/api/v1/app/catalogo/{id}", id)).andExpect(status().isUnauthorized());
		mockMvc.perform(
				get("/api/v1/app/catalogo/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeGerente))
				.andExpect(status().isForbidden());
	}

	private int agregar(String modelo, int anio, String version, Integer precio) throws Exception {
		String cuerpo = """
				{"marca":"%s","modelo":"%s","anio":%d,"version":%s,"precioDesde":%s}""".formatted(marca, modelo, anio,
				version == null ? "null" : "\"" + version + "\"", precio == null ? "null" : precio);
		String respuesta = mockMvc
				.perform(post("/api/v1/catalogo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeGerente)
						.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}
}
