package dev.latticesystems.erp_agente360.backend_agencia360.shared;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Contenedores de Testcontainers administrados como beans de Spring. Así Spring
 * los detiene después de cerrar el contexto (y el pool de conexiones), en lugar
 * de que JUnit los detenga al terminar cada clase de prueba mientras el
 * contexto sigue en caché con conexiones a una base de datos que ya no existe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>("postgres:16-alpine");
	}
}
