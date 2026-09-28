package dev.latticesystems.erp_agente360.backend_agencia360.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API vía springdoc-openapi. De esta única API documentada
 * se generan los clientes de React (TypeScript) y Flutter (Dart) con
 * openapi-generator.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Agencia360 API", version = "v1"), security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
