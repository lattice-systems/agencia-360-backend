package dev.latticesystems.erp_agente360.backend_agencia360.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base para pruebas de integración contra una base de datos real vía
 * Testcontainers, en lugar de mocks, según la estrategia de pruebas del
 * documento de arquitectura. Los contenedores viven en
 * {@link TestcontainersConfiguration}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
