package dev.latticesystems.erp_agente360.backend_agencia360;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifica que los módulos declarados (uno por área funcional) no tengan ciclos ni
 * accedan a paquetes internos de otro módulo, según las reglas de Spring Modulith.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(BackendAgencia360Application.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}
}
