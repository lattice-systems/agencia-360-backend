package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.CuentasAppService;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.NuevoCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.application.RegistroClientesService;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2-M01.09: ver y cerrar las sesiones abiertas de la cuenta.
 *
 * <p>
 * La historia responde una pregunta de seguridad -«¿quién está dentro de mi
 * cuenta ahora?»-, así que lo que más se prueba aquí no es que la lista salga,
 * sino las dos cosas que la harían inservible o mentirosa: que el nombre del
 * dispositivo y el inicio real <strong>sobrevivan al refresco</strong>, porque
 * cada refresco crea una fila nueva; y que una sesión de otra cuenta responda
 * 404 y no 403, para no revelar que existe.
 */
@AutoConfigureMockMvc
@Transactional
class SesionesActivasAppIntegrationTest extends AbstractIntegrationTest {

	private static final String AUTH = "/api/v1/app/auth";
	private static final String SESIONES = "/api/v1/app/sesiones";
	private static final String CORREO = "ana.lopez@correo.com";
	private static final String CONTRASENA = "Temporal123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistroClientesService registroClientesService;

	@Autowired
	private CuentasAppService cuentasAppService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	private Long clienteId;

	@BeforeEach
	void abrirCuenta() {
		clienteId = registroClientesService.registrar(new NuevoCliente("Ana López", CORREO, null, null)).id();
		cuentasAppService.abrir(clienteId, CONTRASENA);
	}

	@Test
	void laListaTraeElDispositivoQueMandoLaApp() throws Exception {
		String token = accessTokenDe(login("Pixel 7 · Android 14"));

		sesiones(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].dispositivo").value("Pixel 7 · Android 14"))
				.andExpect(jsonPath("$[0].id").isNumber()).andExpect(jsonPath("$[0].expiraEn").isNotEmpty())
				.andExpect(jsonPath("$[0].iniciadaEn").isNotEmpty())
				// Nunca se ha refrescado: no hay último uso que decir. Llega como nulo
				// explícito y no ausente, igual que responde `mock_api`, para que la app
				// vea la misma forma contra las dos.
				.andExpect(jsonPath("$[0].ultimoUso").isEmpty());
	}

	@Test
	void sinDispositivoLaSesionSeAbreIgualYLlegaNulo() throws Exception {
		// El contrato lo declara opcional: un cliente viejo tiene que poder entrar.
		String token = accessTokenDe(login(null));

		sesiones(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].dispositivo").isEmpty());
	}

	@Test
	void elNombreYElInicioSobrevivenAlRefresco() throws Exception {
		// Lo más fácil de romper de toda la historia: `emitirSesion` crea una fila
		// nueva en cada refresco. Si no se arrastran, el nombre se pierde a la hora
		// de haber entrado y la lista queda inservible.
		ResultActions entrada = login("Pixel 7");
		String refresh = refreshTokenDe(entrada);

		String token = accessTokenDe(refrescar(refresh).andExpect(status().isOk()));

		sesiones(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].dispositivo").value("Pixel 7"))
				.andExpect(jsonPath("$[0].iniciadaEn").isNotEmpty())
				// Y ahora sí hubo un refresco, así que hay último uso.
				.andExpect(jsonPath("$[0].ultimoUso").isNotEmpty());
	}

	@Test
	void elInicioEsElDeVerdadYNoElDelUltimoRefresco() throws Exception {
		String refresh = refreshTokenDe(login("Pixel 7"));
		String iniciadaAlEntrar = unicaFila("iniciada_en");

		refrescar(refresh).andExpect(status().isOk());

		// `created_at` de la fila nueva es de ahora; `iniciada_en` tiene que seguir
		// siendo el de la entrada, o una sesión de 20 días se vería «iniciada hace
		// un rato» y el dato no serviría para nada.
		assertThat(unicaFila("iniciada_en")).isEqualTo(iniciadaAlEntrar);
	}

	@Test
	void laListaNoTraeLasRevocadas() throws Exception {
		String token = accessTokenDe(login("Pixel 7"));
		String segundo = refreshTokenDe(login("iPhone 14"));
		sesiones(token).andExpect(jsonPath("$.length()").value(2));

		cerrarSesionCon(segundo);

		sesiones(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].dispositivo").value("Pixel 7"));
	}

	@Test
	void laListaNoTraeLasVencidas() throws Exception {
		// Una sesión vencida no está revocada: su `revocada_en` sigue nulo, así que
		// filtrar solo por eso la dejaría en la lista y el cliente vería como
		// abierto un dispositivo que ya no puede entrar.
		String token = accessTokenDe(login("Pixel 7"));
		refreshTokenDe(login("iPhone 14"));
		sesiones(token).andExpect(jsonPath("$.length()").value(2));

		vencer("iPhone 14");

		sesiones(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].dispositivo").value("Pixel 7"));
	}

	@Test
	void cerrarUnaSesionLaRevocaYSuRefreshDejaDeServir() throws Exception {
		String token = accessTokenDe(login("Pixel 7"));
		String refreshAjeno = refreshTokenDe(login("iPhone 14"));
		long idDelOtro = idDeLaSesionCon(token, "iPhone 14");

		mockMvc.perform(delete(SESIONES + "/" + idDelOtro).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());

		// Es el punto de la historia: ese dispositivo ya no puede renovar.
		refrescar(refreshAjeno).andExpect(status().isUnauthorized());
		// Y la del cliente sigue abierta. Esta es la línea que destapó el defecto:
		// el reintento del dispositivo cerrado llegaba con un token revocado, el
		// servidor lo tomaba como robo y cerraba TODAS las sesiones de la cuenta,
		// así que cerrar la sesión de una tablet sacaba al cliente de su propio
		// celular. Ahora solo delata robo un token que consumió un refresco.
		sesiones(token).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void elDispositivoCerradoPuedeReintentarVariasVecesSinTirarLaCuenta() throws Exception {
		// Un dispositivo cerrado no reintenta una vez: reintenta cada vez que se
		// abre la app. Ninguno de esos intentos puede cerrar nada más.
		String token = accessTokenDe(login("Pixel 7"));
		String refreshAjeno = refreshTokenDe(login("iPhone 14"));
		long idDelOtro = idDeLaSesionCon(token, "iPhone 14");
		mockMvc.perform(delete(SESIONES + "/" + idDelOtro).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());

		for (int i = 0; i < 3; i++) {
			refrescar(refreshAjeno).andExpect(status().isUnauthorized());
		}

		assertThat(vigentes()).isEqualTo(1);
	}

	@Test
	void reusarUnTokenGastadoPorUnRefrescoSigueCerrandoTodo() throws Exception {
		// La protección contra robo **no se debilita**: un token que ya se cambió
		// por otro y vuelve a aparecer sí delata que alguien tiene una copia, y
		// ahí sí se cierra toda la cuenta. Es el único caso que la delata.
		String gastado = refreshTokenDe(login("Pixel 7"));
		refrescar(gastado).andExpect(status().isOk());
		refreshTokenDe(login("iPhone 14"));
		assertThat(vigentes()).isEqualTo(2);

		refrescar(gastado).andExpect(status().isUnauthorized());

		assertThat(vigentes()).isZero();
	}

	@Test
	void cerrarDosVecesLaMismaSesionResponde204LasDosVeces() throws Exception {
		// Idempotente a propósito: la app puede reintentar sin saber si la primera
		// llamada llegó.
		String token = accessTokenDe(login("Pixel 7"));
		refreshTokenDe(login("iPhone 14"));
		long idDelOtro = idDeLaSesionCon(token, "iPhone 14");

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(delete(SESIONES + "/" + idDelOtro).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isNoContent());
		}
	}

	@Test
	void unaSesionDeOtraCuentaResponde404YNoSeToca() throws Exception {
		// 404 y no 403: un 403 diría «existe, pero no es tuya». Es la regla del
		// contrato para cualquier recurso ajeno.
		String token = accessTokenDe(login("Pixel 7"));
		String refreshAjeno = refreshDeOtraCuenta();
		long idAjena = idDeLaOtraCuenta();

		mockMvc.perform(delete(SESIONES + "/" + idAjena).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());

		// Y sigue viva: nadie pudo cerrar la sesión de otro.
		refrescar(refreshAjeno).andExpect(status().isOk());
	}

	@Test
	void unaSesionQueNoExisteResponde404() throws Exception {
		String token = accessTokenDe(login("Pixel 7"));

		mockMvc.perform(delete(SESIONES + "/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void cerrarTodasIncluyeLaDeQuienLlama() throws Exception {
		// Es «cerrar sesión en todos los dispositivos», así que la propia también.
		// Si no se incluyera, el cliente creería que echó a todos menos a sí mismo
		// sin haberlo pedido.
		String refreshPropio = refreshTokenDe(login("Pixel 7"));
		String token = accessTokenDe(login("iPhone 14"));
		refreshTokenDe(login("iPad"));

		mockMvc.perform(delete(SESIONES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());

		refrescar(refreshPropio).andExpect(status().isUnauthorized());
		assertThat(vigentes()).isZero();
	}

	@Test
	void cerrarTodasNoTocaLasDeOtrasCuentas() throws Exception {
		String token = accessTokenDe(login("Pixel 7"));
		String refreshAjeno = refreshDeOtraCuenta();

		mockMvc.perform(delete(SESIONES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNoContent());

		refrescar(refreshAjeno).andExpect(status().isOk());
	}

	@Test
	void lasRutasDeSesionesExigenSesionDeCliente() throws Exception {
		mockMvc.perform(get(SESIONES)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(SESIONES)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(SESIONES + "/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void unNombreDeDispositivoDemasiadoLargoEsDatosInvalidos() throws Exception {
		// El contrato declara 80. La app recorta antes de mandar, así que esto no
		// debería pasar nunca; si pasa, es mejor un 400 claro que un nombre cortado
		// en silencio por la base de datos.
		login("a".repeat(81)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	private ResultActions login(String dispositivo) throws Exception {
		String cuerpo = dispositivo == null
				? "{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(CORREO, CONTRASENA)
				: "{\"correo\":\"%s\",\"contrasena\":\"%s\",\"dispositivo\":\"%s\"}".formatted(CORREO, CONTRASENA,
						dispositivo);
		return mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions refrescar(String refreshToken) throws Exception {
		return mockMvc.perform(post(AUTH + "/refrescar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private void cerrarSesionCon(String refreshToken) throws Exception {
		mockMvc.perform(post(AUTH + "/cerrar-sesion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isNoContent());
	}

	private ResultActions sesiones(String token) throws Exception {
		return mockMvc.perform(get(SESIONES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	/** El id de la sesión de ese dispositivo, leído de la lista. */
	private long idDeLaSesionCon(String token, String dispositivo) throws Exception {
		String cuerpo = sesiones(token).andReturn().getResponse().getContentAsString();
		List<Integer> ids = JsonPath.read(cuerpo, "$[?(@.dispositivo == '" + dispositivo + "')].id");
		assertThat(ids).hasSize(1);
		return ids.get(0).longValue();
	}

	/** Deja vencida la sesión de ese dispositivo, sin revocarla. */
	private void vencer(String dispositivo) {
		entityManager.flush();
		int filas = jdbcTemplate.update(
				"update sesion_app set expira_en = now() - interval '1 day' where dispositivo = ?", dispositivo);
		assertThat(filas).isEqualTo(1);
		entityManager.clear();
	}

	private String refreshDeOtraCuenta() throws Exception {
		Long otro = registroClientesService
				.registrar(new NuevoCliente("Luis Pérez", "luis.perez@correo.com", null, null)).id();
		cuentasAppService.abrir(otro, CONTRASENA);
		return refreshTokenDe(mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"luis.perez@correo.com\",\"contrasena\":\"%s\"}".formatted(CONTRASENA))));
	}

	private long idDeLaOtraCuenta() {
		entityManager.flush();
		return jdbcTemplate.queryForObject(
				"select s.id from sesion_app s join cuenta_app c on c.id = s.cuenta_id where c.cliente_id <> ?",
				Long.class, clienteId);
	}

	private String unicaFila(String columna) {
		entityManager.flush();
		return jdbcTemplate.queryForObject(
				("select cast(%s as varchar) from sesion_app s join cuenta_app c on c.id = s.cuenta_id "
						+ "where c.cliente_id = ? and s.revocada_en is null").formatted(columna),
				String.class, clienteId);
	}

	private int vigentes() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("select count(*) from sesion_app s join cuenta_app c on c.id = s.cuenta_id "
				+ "where c.cliente_id = ? and s.revocada_en is null", Integer.class, clienteId);
	}

	private static String accessTokenDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.accessToken");
	}

	private static String refreshTokenDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.refreshToken");
	}
}
