package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AlmacenManuales;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Los PDF de los manuales viven en la base: un reinicio del contenedor (Azure
 * Container Apps no guarda disco) ya no los borra.
 */
class AlmacenManualesEnBaseIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AlmacenManuales almacen;

	@Test
	void elAlmacenPorDefectoEsLaBase() {
		assertThat(almacen).isInstanceOf(AlmacenManualesEnBase.class);
	}

	@Test
	void guardaLeeYBorraUnArchivo() {
		byte[] pdf = "%PDF-1.4 prueba".getBytes(StandardCharsets.US_ASCII);

		String clave = almacen.guardar(pdf);

		assertThat(clave).matches("[0-9a-f\\-]{36}\\.pdf");
		assertThat(almacen.leer(clave)).hasValue(pdf);

		almacen.borrar(clave);
		assertThat(almacen.leer(clave)).isEmpty();
	}

	@Test
	void unaClaveQueNoExisteNoTieneArchivo() {
		assertThat(almacen.leer("00000000-0000-0000-0000-000000000000.pdf")).isEmpty();
		almacen.borrar("00000000-0000-0000-0000-000000000000.pdf");
	}
}
