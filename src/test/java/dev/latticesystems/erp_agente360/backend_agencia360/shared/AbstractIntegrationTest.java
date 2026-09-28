package dev.latticesystems.erp_agente360.backend_agencia360.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para pruebas de integración contra una base de datos real vía Testcontainers,
 * en lugar de mocks, según la estrategia de pruebas del documento de arquitectura.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
