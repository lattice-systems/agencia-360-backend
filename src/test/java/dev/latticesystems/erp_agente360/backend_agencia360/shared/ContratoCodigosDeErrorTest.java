package dev.latticesystems.erp_agente360.backend_agencia360.shared;

import static org.assertj.core.api.Assertions.assertThat;

import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.CodigosError;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Los códigos de error que el backend puede responder son los mismos que el
 * contrato de la app declara en {@code Problema.codigo}. La app decide por
 * {@code codigo}, nunca por el texto, y su prueba de contrato exige que el enum
 * sea el conjunto que ella conoce: un código que falte aquí rompe el CI de la
 * app en un PR ajeno (pasó con ESTRENO_SIN_PRECIO y ESTRENO_DUPLICADA).
 */
class ContratoCodigosDeErrorTest {

	private static final Path CONTRATO = Path.of("docs/api/agencia360-api-app-v2.yaml");

	/**
	 * Códigos que solo responden endpoints del personal (ERP y CRM): la app nunca
	 * los ve, así que el contrato de la app no los declara. Cada uno con su porqué.
	 */
	private static final Set<String> SOLO_DEL_PERSONAL = Set.of(
			// POST /api/v1/taller/ordenes: el vehículo ya tiene una orden sin entregar.
			"ORDEN_ABIERTA");

	@Test
	void cadaCodigoDelBackendEstaEnElContratoYAlReves() throws Exception {
		Set<String> delBackend = new TreeSet<>(codigosDelBackend());
		assertThat(delBackend).as("SOLO_DEL_PERSONAL nombra un código que ya no existe en CodigosError")
				.containsAll(SOLO_DEL_PERSONAL);
		Set<String> enBackend = new TreeSet<>(delBackend);
		enBackend.removeAll(SOLO_DEL_PERSONAL);
		Set<String> enContrato = new TreeSet<>(codigosDelContrato());

		Set<String> faltanEnElContrato = new TreeSet<>(enBackend);
		faltanEnElContrato.removeAll(enContrato);
		Set<String> sobranEnElContrato = new TreeSet<>(enContrato);
		sobranEnElContrato.removeAll(enBackend);

		assertThat(faltanEnElContrato)
				.as("Códigos de CodigosError que el contrato no declara en Problema.codigo (agrégalos al enum de "
						+ CONTRATO + " y sube su versión)")
				.isEmpty();
		assertThat(sobranEnElContrato)
				.as("Códigos que el contrato declara pero CodigosError no tiene (quítalos del enum o créalos)")
				.isEmpty();
	}

	private static Set<String> codigosDelBackend() throws IllegalAccessException {
		Set<String> codigos = new TreeSet<>();
		for (var campo : CodigosError.class.getDeclaredFields()) {
			if (Modifier.isPublic(campo.getModifiers()) && Modifier.isStatic(campo.getModifiers())
					&& campo.getType() == String.class) {
				codigos.add((String) campo.get(null));
			}
		}
		return codigos;
	}

	@SuppressWarnings("unchecked")
	private static List<String> codigosDelContrato() throws IOException {
		Map<String, Object> raiz = new Yaml().load(Files.readString(CONTRATO));
		Map<String, Object> componentes = (Map<String, Object>) raiz.get("components");
		Map<String, Object> esquemas = (Map<String, Object>) componentes.get("schemas");
		Map<String, Object> problema = (Map<String, Object>) esquemas.get("Problema");
		Map<String, Object> propiedades = (Map<String, Object>) problema.get("properties");
		Map<String, Object> codigo = (Map<String, Object>) propiedades.get("codigo");
		return (List<String>) codigo.get("enum");
	}
}
