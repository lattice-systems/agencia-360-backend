package dev.latticesystems.erp_agente360.backend_agencia360;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifica que los módulos declarados (uno por área funcional) no tengan ciclos
 * ni accedan a paquetes internos de otro módulo, según las reglas de Spring
 * Modulith.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(BackendAgencia360Application.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	void archivosEsUnModuloDeLaV2() {
		assertThat(modules.getModuleByName("archivos")).isPresent();
	}

	@Test
	void notificacionesEsUnModuloQueNoDependeDeCarecarapp() {
		assertThat(modules.getModuleByName("notificaciones")).isPresent();
		// La dirección es carecarapp -> notificaciones; lo contrario sería un ciclo.
		assertThat(modules.getModuleByName("notificaciones").orElseThrow()
				.getDirectDependencies(modules, org.springframework.modulith.core.DependencyType.values()).stream()
				.map(dependencia -> dependencia.getTargetModule().getIdentifier().toString()))
				.doesNotContain("carecarapp");
	}
}
