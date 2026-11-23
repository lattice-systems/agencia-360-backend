package dev.latticesystems.erp_agente360.backend_agencia360.archivos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.latticesystems.erp_agente360.backend_agencia360.archivos.AlmacenArchivos.ArchivoGuardado;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ArchivosIntegrationTest extends AbstractIntegrationTest {

	private static final byte[] PDF = "%PDF-1.4\nevidencia\n%%EOF".getBytes(StandardCharsets.US_ASCII);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AlmacenArchivos almacen;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void guardaEnLaBaseYSeDescargaConLaUrlTemporalSinToken() throws Exception {
		ArchivoGuardado guardado = almacen.guardar("evidencias", "application/pdf", PDF);

		assertThat(jdbcTemplate.queryForObject("select tamano_bytes from archivo where clave = ?", Long.class,
				guardado.clave())).isEqualTo(PDF.length);
		mockMvc.perform(get(almacen.urlTemporal(guardado.clave()))).andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf")).andExpect(content().bytes(PDF));
		assertThat(almacen.leer(guardado.clave()))
				.hasValueSatisfying(archivo -> assertThat(archivo.contenido()).isEqualTo(PDF));
	}

	@Test
	void unaUrlAlteradaOSinFirmaResponde404() throws Exception {
		ArchivoGuardado guardado = almacen.guardar("evidencias", "image/png", new byte[]{1, 2, 3});
		String url = almacen.urlTemporal(guardado.clave());

		mockMvc.perform(get(url.replaceAll("firma=[^&]+", "firma=alterada"))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/archivos/" + guardado.clave())).andExpect(status().isNotFound());
	}

	@Test
	void unArchivoBorradoYaNoSeDescarga() throws Exception {
		ArchivoGuardado guardado = almacen.guardar("documentos", "image/jpeg", new byte[]{9, 9});
		String url = almacen.urlTemporal(guardado.clave());

		almacen.borrar(guardado.clave());

		mockMvc.perform(get(url)).andExpect(status().isNotFound());
		assertThat(almacen.leer(guardado.clave())).isEmpty();
	}
}
