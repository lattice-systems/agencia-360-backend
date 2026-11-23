package dev.latticesystems.erp_agente360.backend_agencia360.archivos.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DepositoLocalTest {

	@TempDir
	private Path carpeta;

	@Test
	void guardaLeeYBorraConSuTipo() {
		DepositoLocal deposito = new DepositoLocal(carpeta.toString());
		byte[] contenido = {1, 2, 3};

		deposito.guardar("evidencias/abc", "image/png", contenido);

		assertThat(deposito.leer("evidencias/abc")).hasValueSatisfying(archivo -> {
			assertThat(archivo.tipoContenido()).isEqualTo("image/png");
			assertThat(archivo.contenido()).containsExactly(1, 2, 3);
		});
		deposito.borrar("evidencias/abc");
		assertThat(deposito.leer("evidencias/abc")).isEmpty();
	}

	@Test
	void noSaleDeSuCarpeta() {
		DepositoLocal deposito = new DepositoLocal(carpeta.resolve("archivos").toString());

		assertThatThrownBy(() -> deposito.guardar("../fuera", "image/png", new byte[]{1}))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
